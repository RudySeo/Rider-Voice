package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeCommand
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrant
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrantStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.error.InvalidOAuthExchangeCodeException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AuthServiceTest {

    private val now = Instant.parse("2026-07-23T01:02:03Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = mock(UserStore::class.java)
    private val accounts = mock(OAuthAccountStore::class.java)
    private val sessions = mock(UserSessionStore::class.java)
    private val exchangeGrants = mock(OAuthExchangeGrantStore::class.java)
    private val auth = AuthService(users, accounts, sessions, exchangeGrants, clock)

    @Test
    fun `provider login creates a new social account and returns only a hashed exchange grant`() {
        val command = CompleteSocialLoginCommand(OAuthProvider.KAKAO, "provider-subject")
        `when`(accounts.findOAuthAccount(command.provider, command.providerSubject)).thenReturn(null)
        `when`(users.saveUser(anyValue())).thenAnswer {
            (it.arguments[0] as User).apply { id = 10L }
        }
        `when`(accounts.saveOAuthAccount(anyValue()))
            .thenAnswer { it.arguments[0] as OAuthAccount }

        val result = auth.complete(command)

        assertThat(result.code).isNotBlank()
        val savedAccount = savedArgument<OAuthAccount>(accounts, "saveOAuthAccount")
        assertThat(savedAccount.user.id).isEqualTo(10L)
        assertThat(savedAccount.provider).isEqualTo(OAuthProvider.KAKAO)
        assertThat(savedAccount.providerSubject).isEqualTo("provider-subject")
        val savedHash = savedArguments(exchangeGrants, "save").first() as String
        val savedGrant = savedArguments(exchangeGrants, "save").last() as OAuthExchangeGrant
        assertThat(savedHash).isEqualTo(sha256(result.code))
        assertThat(savedHash).isNotEqualTo(result.code)
        assertThat(savedGrant.userId).isEqualTo(10L)
        assertThat(savedGrant.expiresAt).isEqualTo(now.plusSeconds(60))
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `valid exchange code for an active account issues opaque service tokens`() {
        val user = activeUser()
        val originalTermsAgreedAt = user.termsAgreedAt
        val account = OAuthAccount(user, OAuthProvider.KAKAO, "active-subject")
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "active-subject")).thenReturn(account)
        `when`(users.findUserForUpdate(user.id)).thenReturn(user)
        `when`(users.findUser(user.id)).thenReturn(user)
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }

        val code = auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "active-subject")).code
        val grant = savedArguments(exchangeGrants, "save").last() as OAuthExchangeGrant
        `when`(exchangeGrants.consume(sha256(code), now)).thenReturn(grant)
        val result = auth.exchange(ExchangeSocialLoginCodeCommand(code))

        assertThat(result.accessToken).isNotBlank()
        assertThat(result.refreshToken).isNotBlank()
        assertThat(result.user.termsVersion).isEqualTo("2026-07-01")
        assertThat(user.termsAgreedAt).isEqualTo(originalTermsAgreedAt)
        verify(users).findUserForUpdate(user.id)
        val savedSession = savedArgument<UserSession>(sessions, "saveSession")
        assertThat(savedSession.refreshTokenHash).isEqualTo(sha256(result.refreshToken))
        assertThat(savedSession.refreshTokenHash).isNotEqualTo(result.refreshToken)
    }

    @Test
    fun `valid exchange code for a pending account records current terms and issues service tokens`() {
        val user = User().apply { id = 8L }
        `when`(users.findUserForUpdate(user.id)).thenReturn(user)
        `when`(exchangeGrants.consume(sha256("pending-code"), now))
            .thenReturn(OAuthExchangeGrant(user.id, now.plusSeconds(60)))
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.exchange(ExchangeSocialLoginCodeCommand("pending-code"))

        assertThat(result.user.id).isEqualTo(user.id)
        assertThat(result.user.status).isEqualTo("ACTIVE")
        assertThat(result.user.termsVersion).isEqualTo("2026-07-01")
        assertThat(result.accessToken).isNotBlank()
        assertThat(result.refreshToken).isNotBlank()
    }

    @Test
    fun `invalid expired or reused exchange code is rejected with the same authentication error`() {
        listOf("invalid-code", "expired-code", "reused-code").forEach { code ->
            `when`(exchangeGrants.consume(sha256(code), now)).thenReturn(null)

            assertThrows<InvalidOAuthExchangeCodeException> {
                auth.exchange(ExchangeSocialLoginCodeCommand(code))
            }
        }

        verify(users, never()).findUserForUpdate(org.mockito.ArgumentMatchers.anyLong())
        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `suspended social account cannot exchange for service tokens`() {
        val user = activeUser()
        setStatus(user, UserStatus.SUSPENDED)
        `when`(users.findUserForUpdate(user.id)).thenReturn(user)
        `when`(exchangeGrants.consume(sha256("suspended-code"), now))
            .thenReturn(OAuthExchangeGrant(user.id, now.plusSeconds(60)))

        assertThrows<AuthenticationRequiredException> {
            auth.exchange(ExchangeSocialLoginCodeCommand("suspended-code"))
        }

        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `access token authentication reflects the current database role`() {
        val original = activeUser(UserRole.USER)
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "role-subject"))
            .thenReturn(OAuthAccount(original, OAuthProvider.KAKAO, "role-subject"))
        `when`(users.findUserForUpdate(original.id)).thenReturn(original)
        `when`(users.findUser(original.id)).thenReturn(original)
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }
        val code = auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "role-subject")).code
        val grant = savedArguments(exchangeGrants, "save").last() as OAuthExchangeGrant
        `when`(exchangeGrants.consume(sha256(code), now)).thenReturn(grant)
        val accessToken = auth.exchange(ExchangeSocialLoginCodeCommand(code)).accessToken
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
        val service = AuthService(users, accounts, sessions, exchangeGrants, mutableClock)
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
        val restarted = AuthService(users, accounts, sessions, exchangeGrants, clock)

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

    private fun savedArguments(mock: Any, methodName: String): List<Any?> = mockingDetails(mock)
        .invocations
        .single { it.method.name == methodName }
        .arguments
        .toList()
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
