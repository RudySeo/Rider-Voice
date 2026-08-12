package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
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

class AuthServiceTest {

    private val now = Instant.parse("2026-07-23T01:02:03Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = mock(UserStore::class.java)
    private val accounts = mock(OAuthAccountStore::class.java)
    private val sessions = mock(UserSessionStore::class.java)
    private val auth = AuthService(users, accounts, sessions, clock)

    @Test
    fun `provider login creates a new active social account and stores only a hashed refresh token`() {
        val command = CompleteSocialLoginCommand(OAuthProvider.KAKAO, "provider-subject")
        `when`(accounts.findOAuthAccount(command.provider, command.providerSubject)).thenReturn(null)
        `when`(users.saveUser(anyValue())).thenAnswer {
            (it.arguments[0] as User).apply { id = 10L }
        }
        `when`(accounts.saveOAuthAccount(anyValue()))
            .thenAnswer { it.arguments[0] as OAuthAccount }
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.complete(command)

        assertThat(result.refreshToken).isNotBlank()
        val savedAccount = savedArgument<OAuthAccount>(accounts, "saveOAuthAccount")
        assertThat(savedAccount.user.id).isEqualTo(10L)
        assertThat(savedAccount.provider).isEqualTo(OAuthProvider.KAKAO)
        assertThat(savedAccount.providerSubject).isEqualTo("provider-subject")
        val savedSession = savedArgument<UserSession>(sessions, "saveSession")
        assertThat(savedSession.refreshTokenHash).isEqualTo(sha256(result.refreshToken))
        assertThat(savedSession.refreshTokenHash).isNotEqualTo(result.refreshToken)
        assertThat(savedSession.expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        assertThat(savedAccount.user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(savedAccount.user.role).isEqualTo(UserRole.USER)
    }

    @Test
    fun `provider login for an active account creates a refresh session`() {
        val user = activeUser()
        val account = OAuthAccount(user, OAuthProvider.KAKAO, "active-subject")
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "active-subject")).thenReturn(account)
        `when`(users.findUserForUpdate(user.id)).thenReturn(user)
        `when`(sessions.saveSession(anyValue()))
            .thenAnswer { it.arguments[0] as UserSession }

        val result = auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "active-subject"))

        assertThat(result.refreshToken).isNotBlank()
        verify(users).findUserForUpdate(user.id)
        val savedSession = savedArgument<UserSession>(sessions, "saveSession")
        assertThat(savedSession.refreshTokenHash).isEqualTo(sha256(result.refreshToken))
        assertThat(savedSession.refreshTokenHash).isNotEqualTo(result.refreshToken)
    }

    @Test
    fun `suspended social account cannot create a refresh session`() {
        val user = activeUser()
        setStatus(user, UserStatus.SUSPENDED)
        `when`(accounts.findOAuthAccount(OAuthProvider.KAKAO, "suspended-subject"))
            .thenReturn(OAuthAccount(user, OAuthProvider.KAKAO, "suspended-subject"))
        `when`(users.findUserForUpdate(user.id)).thenReturn(user)

        assertThrows<AuthenticationRequiredException> {
            auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "suspended-subject"))
        }

        verify(sessions, never()).saveSession(anyValue())
    }

    @Test
    fun `access token authentication reflects the current database role`() {
        val original = activeUser(UserRole.USER)
        `when`(users.findUser(original.id)).thenReturn(original)
        val accessToken = issueTokens(auth, original).accessToken
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
        val service = AuthService(users, accounts, sessions, mutableClock)
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
        val restarted = AuthService(users, accounts, sessions, clock)

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

        auth.logout(LogoutCommand(tokens.refreshToken))
        auth.logout(LogoutCommand(tokens.refreshToken))

        assertThat(session.revokedAt).isEqualTo(now)
        assertThat(auth.authenticate(tokens.accessToken)).isNull()
        verify(sessions, times(2)).findSessionForUpdate(sha256(tokens.refreshToken))
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

        val refreshedTokens = auth.refresh(RefreshSessionCommand(rawRefreshToken))

        assertThat(initialSession.revokedAt).isEqualTo(now)
        assertThat(initialSession.rotatedToSession).isSameAs(savedSessions.last())
        assertThat(savedSessions.last().expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        assertThat(auth.authenticate(refreshedTokens.accessToken))
            .isEqualTo(AuthenticatedUserPrincipal(user.id))
        assertThrows<AuthenticationRequiredException> { auth.refresh(RefreshSessionCommand(rawRefreshToken)) }
        assertThat(savedSessions).hasSize(1)
    }

    @Test
    fun `expired refresh token cannot create a successor session`() {
        val user = User().apply { id = 3L }
        val rawRefreshToken = "expired-refresh-token"
        val expiredSession = UserSession(
            user = user,
            refreshTokenHash = sha256(rawRefreshToken),
            expiresAt = now,
        )
        `when`(sessions.findSessionForUpdate(expiredSession.refreshTokenHash)).thenReturn(expiredSession)

        assertThrows<AuthenticationRequiredException> { auth.refresh(RefreshSessionCommand(rawRefreshToken)) }

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
        return service.refresh(RefreshSessionCommand(rawRefreshToken))
    }

    private fun activeUser(role: UserRole = UserRole.USER) = User().apply { id = 9L }.also {
        setRole(it, role)
    }

    private fun setRole(user: User, role: UserRole) {
        User::class.java.getDeclaredField("role").also { field ->
            field.isAccessible = true
            field.set(user, role)
        }
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
