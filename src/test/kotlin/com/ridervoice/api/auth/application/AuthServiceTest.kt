package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthServiceTest {

    private val now = Instant.parse("2026-07-23T01:02:03Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = mock(UserRepository::class.java)
    private val sessions = mock(UserSessionRepository::class.java)
    private val onboardingTokens = mock(OnboardingTokenRepository::class.java)
    private val auth = AuthService(users, sessions, onboardingTokens, clock)

    @Test
    fun `access token expires after fifteen minutes`() {
        val mutableClock = MutableClock(now)
        val service = AuthService(users, sessions, onboardingTokens, mutableClock)
        val user = activeUser()
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))

        val accessToken = issueTokens(service, user).accessToken

        mutableClock.advance(Duration.ofMinutes(15).minusMillis(1))
        assertThat(service.authenticate(accessToken)).isEqualTo(AuthenticatedUserPrincipal(user.id))

        mutableClock.advance(Duration.ofMillis(1))
        assertThat(service.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is rejected when current user is suspended or withdrawn`() {
        val user = activeUser()
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        val accessToken = issueTokens(auth, user).accessToken

        setStatus(user, UserStatus.SUSPENDED)
        assertThat(auth.authenticate(accessToken)).isNull()

        setStatus(user, UserStatus.WITHDRAWN)
        assertThat(auth.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is invalid after service restart`() {
        val accessToken = issueTokens(auth, activeUser()).accessToken
        val restarted = AuthService(users, sessions, onboardingTokens, clock)

        assertThat(restarted.authenticate(accessToken)).isNull()
    }

    @Test
    fun `logout locks and revokes refresh session and removes its access token`() {
        val user = activeUser()
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        val tokens = issueTokens(auth, user)
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
        val user = activeUser()
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        val rawRefreshToken = "initial-refresh-token"
        val initialSession = UserSession(
            user = user,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now.plus(Duration.ofDays(30)),
        )
        val savedSessions = mutableListOf<UserSession>()
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { (it.arguments[0] as UserSession).also(savedSessions::add) }
        `when`(sessions.findByRefreshTokenHashForUpdate(sha256(rawRefreshToken)))
            .thenReturn(Optional.of(initialSession))

        val refreshedTokens = auth.refresh(rawRefreshToken)

        assertThat(initialSession.revokedAt).isEqualTo(now)
        assertThat(initialSession.rotatedToSession).isSameAs(savedSessions.last())
        assertThat(savedSessions.last().expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        assertThat(auth.authenticate(refreshedTokens.accessToken))
            .isEqualTo(AuthenticatedUserPrincipal(user.id))
        assertThrows<IllegalStateException> { auth.refresh(rawRefreshToken) }
        assertThat(savedSessions).hasSize(1)
    }

    @Test
    fun `expired refresh token cannot create a successor session`() {
        val user = User().apply { id = 3L }.also { it.agreeToTerms("2026-07-01", now.minusSeconds(60)) }
        val rawRefreshToken = "expired-refresh-token"
        val expiredSession = UserSession(
            user = user,
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
        val user = User().apply { id = 4L }
        val rawToken = "raw-onboarding-token"
        val token = onboardingToken(user, rawToken)
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
        val user = User().apply { id = 5L }
        val expired = OnboardingToken(user, sha256("expired"), now.minusSeconds(301), now.minusSeconds(1))
        val consumed = onboardingToken(user, "consumed").also { it.consume(now.minusSeconds(1)) }
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
        val owner = User().apply { id = 6L }
        val otherUserId = 7L
        val token = onboardingToken(owner, "owner-token")
        `when`(onboardingTokens.findByTokenHashForUpdate(token.tokenHash)).thenReturn(Optional.of(token))

        assertThrows<AuthenticationRequiredException> {
            auth.agree(OnboardingPrincipal(otherUserId, token.tokenHash), "2026-07-01")
        }

        assertThat(token.consumedAt).isNull()
        verify(users, never()).findById(org.mockito.ArgumentMatchers.anyLong())
        verify(sessions, never()).save(org.mockito.ArgumentMatchers.any(UserSession::class.java))
    }

    @Test
    fun `simultaneous duplicate consent consumes one onboarding token only once`() {
        val user = User().apply { id = 8L }
        val token = onboardingToken(user, "one-use-token")
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

    private fun issueTokens(service: AuthService, user: User): AuthTokens {
        val rawRefreshToken = "initial-refresh-token"
        val initialSession = UserSession(
            user = user,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now.plus(Duration.ofDays(30)),
        )
        `when`(sessions.findByRefreshTokenHashForUpdate(initialSession.refreshTokenHash))
            .thenReturn(Optional.of(initialSession))
        `when`(sessions.save(org.mockito.ArgumentMatchers.any(UserSession::class.java)))
            .thenAnswer { it.arguments[0] as UserSession }
        return service.refresh(rawRefreshToken)
    }

    private fun activeUser() = User().apply { id = 9L }.also {
        it.agreeToTerms("2026-07-01", now.minusSeconds(60))
    }

    private fun setStatus(user: User, status: UserStatus) {
        User::class.java.getDeclaredField("status").also { field ->
            field.isAccessible = true
            field.set(user, status)
        }
    }

    private fun onboardingToken(user: User, rawToken: String) = OnboardingToken(
        user = user,
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
