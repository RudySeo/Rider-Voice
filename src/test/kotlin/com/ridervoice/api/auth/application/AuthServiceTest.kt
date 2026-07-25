package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.OnboardingTokenStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OnboardingPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthServiceTest {

    private val now = Instant.parse("2026-07-23T01:02:03Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = mock(UserStore::class.java)
    private val accounts = mock(OAuthAccountStore::class.java)
    private val sessions = mock(UserSessionStore::class.java)
    private val onboardingTokens = mock(OnboardingTokenStore::class.java)
    private val auth = AuthService(users, accounts, sessions, onboardingTokens, clock)

    @Test
    fun `new social account is created as a user and receives only an onboarding token`() {
        val command = CompleteSocialLoginCommand(OAuthProvider.KAKAO, "provider-subject")
        `when`(accounts.findOAuthAccount(command.provider, command.providerSubject)).thenReturn(null)
        `when`(users.saveUser(anyValue())).thenAnswer {
            (it.arguments[0] as User).apply { id = 10L }
        }
        `when`(accounts.saveOAuthAccount(anyValue()))
            .thenAnswer { it.arguments[0] as OAuthAccount }
        `when`(onboardingTokens.saveOnboardingToken(anyValue()))
            .thenAnswer { it.arguments[0] as OnboardingToken }

        val result = auth.complete(command)

        assertThat(result.user.id).isEqualTo(10L)
        assertThat(result.user.role).isEqualTo(UserRole.USER)
        assertThat(result.termsAgreed).isFalse()
        assertThat(result.onboardingToken).isNotBlank()
        assertThat(result.tokens).isNull()
        val savedAccount = savedArgument<OAuthAccount>(accounts, "saveOAuthAccount")
        assertThat(savedAccount.user.id).isEqualTo(10L)
        assertThat(savedAccount.provider).isEqualTo(OAuthProvider.KAKAO)
        assertThat(savedAccount.providerSubject).isEqualTo("provider-subject")
        val savedToken = savedArgument<OnboardingToken>(onboardingTokens, "saveOnboardingToken")
        assertThat(savedToken.tokenHash).isEqualTo(sha256(result.onboardingToken!!))
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `existing active social account receives opaque service tokens without storing raw refresh token`() {
        val user = activeUser()
        val account = OAuthAccount(user, OAuthProvider.KAKAO, "active-subject")
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "active-subject")).thenReturn(account)
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "active-subject"))

        assertThat(result.termsAgreed).isTrue()
        assertThat(result.onboardingToken).isNull()
        assertThat(result.tokens?.accessToken).isNotBlank()
        assertThat(result.tokens?.refreshToken).isNotBlank()
        val savedSession = savedArgument<UserSession>(sessions, "saveSession")
        assertThat(savedSession.refreshTokenHash).isEqualTo(sha256(result.tokens!!.refreshToken))
        assertThat(savedSession.refreshTokenHash).isNotEqualTo(result.tokens.refreshToken)
    }

    @Test
    fun `suspended social account cannot receive onboarding or service tokens`() {
        val user = activeUser()
        setStatus(user, UserStatus.SUSPENDED)
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "suspended-subject"))
            .thenReturn(OAuthAccount(user, OAuthProvider.KAKAO, "suspended-subject"))

        assertThrows<AuthenticationRequiredException> {
            auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "suspended-subject"))
        }

        verify(onboardingTokens, never()).saveOnboardingToken(anyValue())
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `access token authentication reflects the current database role`() {
        val original = activeUser(UserRole.USER)
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "role-subject"))
            .thenReturn(OAuthAccount(original, OAuthProvider.KAKAO, "role-subject"))
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }
        val accessToken = auth.complete(
            CompleteSocialLoginCommand(OAuthProvider.KAKAO, "role-subject"),
        ).tokens!!.accessToken
        val promoted = activeUser(UserRole.ADMIN)
        `when`(users.findUser(original.id)).thenReturn(promoted)

        assertThat(auth.authenticate(accessToken))
            .isEqualTo(AuthenticatedUserPrincipal(original.id, "ROLE_ADMIN"))
    }

    @Test
    fun `social login completion is transactional`() {
        val method = AuthService::class.java.getMethod(
            "complete",
            CompleteSocialLoginCommand::class.java,
        )

        assertThat(method.getAnnotation(Transactional::class.java)).isNotNull()
    }

    @Test
    fun `access token expires after fifteen minutes`() {
        val mutableClock = MutableClock(now)
        val service = AuthService(users, accounts, sessions, onboardingTokens, mutableClock)
        val user = activeUser()
        `when`(users.findUser(user.id)).thenReturn(user)

        val accessToken = issueTokens(service, user).accessToken

        mutableClock.advance(Duration.ofMinutes(15).minusMillis(1))
        assertThat(service.authenticate(accessToken)).isEqualTo(AuthenticatedUserPrincipal(user.id))

        mutableClock.advance(Duration.ofMillis(1))
        assertThat(service.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is rejected when current user is suspended or withdrawn`() {
        val user = activeUser()
        `when`(users.findUser(user.id)).thenReturn(user)
        val accessToken = issueTokens(auth, user).accessToken

        setStatus(user, UserStatus.SUSPENDED)
        assertThat(auth.authenticate(accessToken)).isNull()

        setStatus(user, UserStatus.WITHDRAWN)
        assertThat(auth.authenticate(accessToken)).isNull()
    }

    @Test
    fun `access token is invalid after service restart`() {
        val accessToken = issueTokens(auth, activeUser()).accessToken
        val restarted = AuthService(users, accounts, sessions, onboardingTokens, clock)

        assertThat(restarted.authenticate(accessToken)).isNull()
    }

    @Test
    fun `logout locks and revokes refresh session and removes its access token`() {
        val user = activeUser()
        `when`(users.findUser(user.id)).thenReturn(user)
        val tokens = issueTokens(auth, user)
        val session = savedArgument<UserSession>(sessions, "saveSession")
        `when`(sessions.findSessionForUpdate(sha256(tokens.refreshToken))).thenReturn(session)

        assertThat(auth.authenticate(tokens.accessToken)).isEqualTo(AuthenticatedUserPrincipal(user.id))

        auth.logout(AuthenticatedUserPrincipal(user.id), tokens.refreshToken)

        assertThat(session.revokedAt).isEqualTo(now)
        assertThat(auth.authenticate(tokens.accessToken)).isNull()
        verify(sessions).findSessionForUpdate(sha256(tokens.refreshToken))
    }

    @Test
    fun `refresh rotates a thirty day session and rejects reuse of the previous refresh token`() {
        val user = activeUser()
        `when`(users.findUser(user.id)).thenReturn(user)
        val rawRefreshToken = "initial-refresh-token"
        val initialSession = UserSession(
            user = user,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now.plus(Duration.ofDays(30)),
        )
        val savedSessions = mutableListOf<UserSession>()
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { (it.arguments[0] as UserSession).also(savedSessions::add) }
        `when`(sessions.findSessionForUpdate(sha256(rawRefreshToken))).thenReturn(initialSession)

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
        `when`(sessions.findSessionForUpdate(expiredSession.refreshTokenHash)).thenReturn(expiredSession)

        assertThrows<IllegalStateException> { auth.refresh(rawRefreshToken) }

        verify(users, never()).findUser(user.id)
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `valid locked onboarding token is consumed with terms agreement and formal token issuance`() {
        val user = User().apply { id = 4L }
        val rawToken = "raw-onboarding-token"
        val token = onboardingToken(user, rawToken)
        `when`(onboardingTokens.findOnboardingTokenForUpdate(token.tokenHash)).thenReturn(token)
        `when`(users.findUser(user.id)).thenReturn(user)
        `when`(sessions.saveSession(anyValue()))
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
        `when`(users.findUser(user.id)).thenReturn(user)

        listOf(expired, consumed).forEach { token ->
            `when`(onboardingTokens.findOnboardingTokenForUpdate(token.tokenHash)).thenReturn(token)
            assertThrows<AuthenticationRequiredException> {
                auth.agree(OnboardingPrincipal(user.id, token.tokenHash), "2026-07-01")
            }
        }

        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `onboarding token cannot be used by a different user`() {
        val owner = User().apply { id = 6L }
        val otherUserId = 7L
        val token = onboardingToken(owner, "owner-token")
        `when`(onboardingTokens.findOnboardingTokenForUpdate(token.tokenHash)).thenReturn(token)

        assertThrows<AuthenticationRequiredException> {
            auth.agree(OnboardingPrincipal(otherUserId, token.tokenHash), "2026-07-01")
        }

        assertThat(token.consumedAt).isNull()
        verify(users, never()).findUser(org.mockito.ArgumentMatchers.anyLong())
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `simultaneous duplicate consent consumes one onboarding token only once`() {
        val user = User().apply { id = 8L }
        val token = onboardingToken(user, "one-use-token")
        `when`(onboardingTokens.findOnboardingTokenForUpdate(token.tokenHash)).thenReturn(token)
        `when`(users.findUser(user.id)).thenReturn(user)
        `when`(sessions.saveSession(anyValue()))
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
        verify(sessions, times(1)).saveSession(anyValue())
    }

    private fun issueTokens(service: AuthService, user: User): AuthTokens {
        val rawRefreshToken = "initial-refresh-token"
        val initialSession = UserSession(
            user = user,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now.plus(Duration.ofDays(30)),
        )
        `when`(sessions.findSessionForUpdate(initialSession.refreshTokenHash)).thenReturn(initialSession)
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }
        return service.refresh(rawRefreshToken)
    }

    private fun activeUser(role: UserRole = UserRole.USER) = User(role).apply { id = 9L }.also {
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyValue(): T {
        org.mockito.ArgumentMatchers.any<T>()
        return null as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> savedArgument(mock: Any, methodName: String): T = mockingDetails(mock)
        .invocations
        .single { it.method.name == methodName }
        .arguments
        .single() as T
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
