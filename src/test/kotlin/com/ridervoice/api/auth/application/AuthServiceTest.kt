package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthLoginState
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.auth.infrastructure.persistence.OAuthAccountRepository
import com.ridervoice.api.auth.infrastructure.persistence.OAuthLoginStateRepository
import com.ridervoice.api.auth.infrastructure.persistence.OnboardingTokenRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserSessionRepository
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OnboardingPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthServiceTest {

    private val now = Instant.parse("2026-07-23T01:02:03Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val kakao = mock(KakaoOAuthPort::class.java)
    private val users = mock(UserRepository::class.java)
    private val accounts = mock(OAuthAccountRepository::class.java)
    private val states = mock(OAuthLoginStateRepository::class.java)
    private val sessions = mock(UserSessionRepository::class.java)
    private val onboardingTokens = mock(OnboardingTokenRepository::class.java)
    private val auth = AuthService(kakao, users, accounts, states, sessions, onboardingTokens, clock)

    @Test
    fun `new user callback returns only a five minute onboarding token and stores only its hash`() {
        prepareCallback("new-subject")
        `when`(accounts.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "new-subject"))
            .thenReturn(Optional.empty())
        `when`(users.save(org.mockito.ArgumentMatchers.any(User::class.java)))
            .thenAnswer { it.arguments[0] as User }

        val result = auth.callback("code", "state")
        val onboardingToken = requireNotNull(result.onboardingToken)

        assertThat(result.tokens).isNull()
        assertThat(onboardingToken).isNotBlank()
        assertThat(result.user.status).isEqualTo("PENDING_TERMS")
        val captor = ArgumentCaptor.forClass(OnboardingToken::class.java)
        verify(onboardingTokens).save(captor.capture())
        assertThat(captor.value.tokenHash).isEqualTo(sha256(onboardingToken))
        assertThat(captor.value.tokenHash).doesNotContain(onboardingToken)
        assertThat(captor.value.expiresAt).isEqualTo(now.plusSeconds(5 * 60L))
        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
        `when`(onboardingTokens.findByTokenHash(captor.value.tokenHash)).thenReturn(Optional.of(captor.value))
        assertThat(auth.authenticate(onboardingToken))
            .isEqualTo(OnboardingPrincipal(captor.value.userId, captor.value.tokenHash))
    }

    @Test
    fun `active user callback returns only regular access and refresh tokens`() {
        prepareCallback("existing-subject")
        val user = User().also { it.agreeToTerms("2026-07-01", now.minusSeconds(60)) }
        `when`(accounts.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "existing-subject"))
            .thenReturn(Optional.of(OAuthAccount(user.id, OAuthProvider.KAKAO, "existing-subject")))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.callback("code", "state")

        assertThat(result.tokens?.accessToken).isNotBlank()
        assertThat(result.tokens?.refreshToken).isNotBlank()
        assertThat(result.onboardingToken).isNull()
        assertThat(result.user.status).isEqualTo("ACTIVE")
        val sessionCaptor = ArgumentCaptor.forClass(UserSession::class.java)
        verify(sessions).save(sessionCaptor.capture())
        assertThat(sessionCaptor.value.expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        verify(onboardingTokens, never()).save(org.mockito.ArgumentMatchers.any(OnboardingToken::class.java))
    }

    @Test
    fun `access token expires after fifteen minutes`() {
        val mutableClock = MutableClock(now)
        val service = AuthService(kakao, users, accounts, states, sessions, onboardingTokens, mutableClock)
        val user = prepareActiveCallback("expiring-access-subject")

        val accessToken = requireNotNull(service.callback("code", "state").tokens).accessToken

        mutableClock.advance(Duration.ofMinutes(15).minusMillis(1))
        assertThat(service.authenticate(accessToken)).isEqualTo(AuthenticatedUserPrincipal(user.id))

        mutableClock.advance(Duration.ofMillis(1))
        assertThat(service.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is rejected when current user is suspended or withdrawn`() {
        val user = prepareActiveCallback("inactive-access-subject")
        val accessToken = requireNotNull(auth.callback("code", "state").tokens).accessToken

        setStatus(user, UserStatus.SUSPENDED)
        assertThat(auth.authenticate(accessToken)).isNull()

        setStatus(user, UserStatus.WITHDRAWN)
        assertThat(auth.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is invalid after service restart`() {
        prepareActiveCallback("restart-access-subject")
        val accessToken = requireNotNull(auth.callback("code", "state").tokens).accessToken
        val restarted = AuthService(kakao, users, accounts, states, sessions, onboardingTokens, clock)

        assertThat(restarted.authenticate(accessToken)).isNull()
    }

    @Test
    fun `logout locks and revokes refresh session and removes its access token`() {
        val user = prepareActiveCallback("logout-subject")
        val tokens = requireNotNull(auth.callback("code", "state").tokens)
        val sessionCaptor = ArgumentCaptor.forClass(UserSession::class.java)
        verify(sessions).save(sessionCaptor.capture())
        val session = sessionCaptor.value
        `when`(sessions.findByRefreshTokenHashForUpdate(sha256(tokens.refreshToken)))
            .thenReturn(Optional.of(session))

        assertThat(auth.authenticate(tokens.accessToken)).isEqualTo(AuthenticatedUserPrincipal(user.id))

        auth.logout(AuthenticatedUserPrincipal(user.id), tokens.refreshToken)

        assertThat(session.revokedAt).isEqualTo(now)
        assertThat(auth.authenticate(tokens.accessToken)).isNull()
        verify(sessions).findByRefreshTokenHashForUpdate(sha256(tokens.refreshToken))
    }

    @Test
    fun `refresh rotates a thirty day session and rejects reuse of the previous refresh token`() {
        val user = prepareActiveCallback("refresh-subject")
        val savedSessions = mutableListOf<UserSession>()
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { (it.arguments[0] as UserSession).also(savedSessions::add) }
        val initialTokens = requireNotNull(auth.callback("code", "state").tokens)
        val initialSession = savedSessions.single()
        `when`(sessions.findByRefreshTokenHashForUpdate(sha256(initialTokens.refreshToken)))
            .thenReturn(Optional.of(initialSession))

        val refreshedTokens = auth.refresh(initialTokens.refreshToken)

        assertThat(initialSession.revokedAt).isEqualTo(now)
        assertThat(initialSession.rotatedToSessionId).isEqualTo(savedSessions.last().id)
        assertThat(savedSessions.last().expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        assertThat(auth.authenticate(refreshedTokens.accessToken))
            .isEqualTo(AuthenticatedUserPrincipal(user.id))
        assertThrows<IllegalStateException> { auth.refresh(initialTokens.refreshToken) }
        assertThat(savedSessions).hasSize(2)
    }

    @Test
    fun `expired refresh token cannot create a successor session`() {
        val user = User().also { it.agreeToTerms("2026-07-01", now.minusSeconds(60)) }
        val rawRefreshToken = "expired-refresh-token"
        val expiredSession = UserSession(
            userId = user.id,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now,
        )
        `when`(sessions.findByRefreshTokenHashForUpdate(expiredSession.refreshTokenHash))
            .thenReturn(Optional.of(expiredSession))

        assertThrows<IllegalStateException> { auth.refresh(rawRefreshToken) }

        verify(users, never()).findById(user.id)
        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
    }

    @Test
    fun `valid locked onboarding token is consumed with terms agreement and formal token issuance`() {
        val user = User()
        val rawToken = "raw-onboarding-token"
        val token = onboardingToken(user.id, rawToken)
        `when`(onboardingTokens.findByTokenHashForUpdate(token.tokenHash)).thenReturn(Optional.of(token))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.agree(OnboardingPrincipal(user.id, token.tokenHash), "2026-07-01")

        assertThat(token.consumedAt).isEqualTo(now)
        assertThat(user.status.name).isEqualTo("ACTIVE")
        assertThat(user.termsVersion).isEqualTo("2026-07-01")
        assertThat(result.accessToken).isNotBlank()
        assertThat(result.refreshToken).isNotBlank()
        assertThat(auth.authenticate(result.accessToken)).isNotNull
    }

    @Test
    fun `expired or consumed onboarding token cannot issue a session`() {
        val user = User()
        val expired = OnboardingToken(user.id, sha256("expired"), now.minusSeconds(301), now.minusSeconds(1))
        val consumed = onboardingToken(user.id, "consumed").also { it.consume(now.minusSeconds(1)) }
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))

        listOf(expired, consumed).forEach { token ->
            `when`(onboardingTokens.findByTokenHashForUpdate(token.tokenHash)).thenReturn(Optional.of(token))
            assertThrows<AuthenticationRequiredException> {
                auth.agree(OnboardingPrincipal(user.id, token.tokenHash), "2026-07-01")
            }
        }

        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
    }

    @Test
    fun `onboarding token cannot be used by a different user`() {
        val owner = User()
        val otherUserId = UUID.randomUUID()
        val token = onboardingToken(owner.id, "owner-token")
        `when`(onboardingTokens.findByTokenHashForUpdate(token.tokenHash)).thenReturn(Optional.of(token))

        assertThrows<AuthenticationRequiredException> {
            auth.agree(OnboardingPrincipal(otherUserId, token.tokenHash), "2026-07-01")
        }

        assertThat(token.consumedAt).isNull()
        verify(users, never()).findById(org.mockito.ArgumentMatchers.any(UUID::class.java))
        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
    }

    @Test
    fun `simultaneous duplicate consent consumes one onboarding token only once`() {
        val user = User()
        val token = onboardingToken(user.id, "one-use-token")
        `when`(onboardingTokens.findByTokenHashForUpdate(token.tokenHash)).thenReturn(Optional.of(token))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { it.arguments[0] as UserSession }
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val futures = (1..2).map {
            executor.submit<Result<AuthTokens>> {
                ready.countDown()
                start.await()
                runCatching { auth.agree(OnboardingPrincipal(user.id, token.tokenHash), "2026-07-01") }
            }
        }
        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        val results = futures.map { it.get(2, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(1)
        assertThat(token.consumedAt).isEqualTo(now)
        verify(sessions, times(1)).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
    }

    private fun prepareCallback(providerSubject: String) {
        val loginState = OAuthLoginState(sha256("state"), now.plusSeconds(300))
        `when`(states.findByStateHash(sha256("state"))).thenReturn(Optional.of(loginState))
        val oauthToken = OAuthAccessToken("kakao-access-token")
        `when`(kakao.exchangeCode("code")).thenReturn(oauthToken)
        `when`(kakao.getUser(oauthToken)).thenReturn(KakaoUserProfile(providerSubject, null))
    }

    private fun prepareActiveCallback(providerSubject: String): User {
        prepareCallback(providerSubject)
        val user = User().also { it.agreeToTerms("2026-07-01", now.minusSeconds(60)) }
        `when`(accounts.findByProviderAndProviderSubject(OAuthProvider.KAKAO, providerSubject))
            .thenReturn(Optional.of(OAuthAccount(user.id, OAuthProvider.KAKAO, providerSubject)))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { it.arguments[0] as UserSession }
        return user
    }

    private fun setStatus(user: User, status: UserStatus) {
        User::class.java.getDeclaredField("status").also { field ->
            field.isAccessible = true
            field.set(user, status)
        }
    }

    private fun onboardingToken(userId: UUID, rawToken: String) = OnboardingToken(
        userId = userId,
        tokenHash = sha256(rawToken),
        issuedAt = now,
        expiresAt = now.plusSeconds(5 * 60L),
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private class MutableClock(
    private var current: Instant,
) : Clock() {
    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
