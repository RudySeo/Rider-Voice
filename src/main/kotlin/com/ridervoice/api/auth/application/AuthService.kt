package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.PrepareMobileLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.ExchangeMobileLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.MobileLoginGrantResult
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.MobileLoginGrantStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.MobileLoginGrant
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.BearerPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class AuthTokens(val accessToken: String, val refreshToken: String, val user: UserSummary)
data class UserSummary(
    val id: Long,
    val status: String,
    val role: UserRole,
)

@Service
class AuthService(
    private val users: UserStore,
    private val accounts: OAuthAccountStore,
    private val sessions: UserSessionStore,
    private val mobileLoginGrants: MobileLoginGrantStore,
    private val clock: Clock = Clock.systemUTC(),
) : PrepareMobileLoginUseCase,
    ExchangeMobileLoginUseCase,
    RefreshSessionUseCase,
    LogoutUseCase,
    GetCurrentUserUseCase,
    AccessTokenAuthenticator {
    private val accessTokens = ConcurrentHashMap<String, AccessTokenRecord>()
    private val random = SecureRandom()

    @Transactional
    override fun prepareMobileLogin(command: CompleteSocialLoginCommand): MobileLoginGrantResult {
        val user = resolveActiveUser(command)
        val rawCode = randomToken()
        mobileLoginGrants.saveGrant(
            MobileLoginGrant(user, hash(rawCode), clock.instant().plus(Duration.ofMinutes(MOBILE_GRANT_EXPIRY_MINUTES))),
        )
        return MobileLoginGrantResult(rawCode)
    }

    @Transactional
    override fun exchangeMobileLogin(code: String): AuthTokens {
        val normalized = code.takeIf(String::isNotBlank) ?: throw AuthenticationRequiredException()
        val grant = mobileLoginGrants.findGrantForUpdate(hash(normalized)) ?: throw AuthenticationRequiredException()
        val now = clock.instant()
        if (!grant.consume(now) || grant.user.status != UserStatus.ACTIVE) throw AuthenticationRequiredException()
        return issueTokens(grant.user).tokens
    }

    @Transactional
    override fun refresh(command: RefreshSessionCommand): AuthTokens {
        val session = sessions.findSessionForUpdate(hash(command.refreshToken))
            ?: throw AuthenticationRequiredException()
        val now = clock.instant()
        if (!session.isActiveAt(now)) {
            throw AuthenticationRequiredException()
        }
        val user = session.user
        if (user.status != UserStatus.ACTIVE) {
            throw AuthenticationRequiredException()
        }
        val next = issueTokens(user)
        session.rotateTo(next.session, now)
        return next.tokens
    }

    @Transactional
    override fun logout(command: LogoutCommand) {
        sessions.findSessionForUpdate(hash(command.refreshToken))
            ?.let { session ->
                if (session.revokedAt == null) {
                    session.revoke(clock.instant())
                }
                accessTokens.entries.removeIf { it.value.sessionId == session.id }
            }
    }

    override fun get(query: GetCurrentUserQuery): UserSummary =
        users.findUser(query.userId)?.let(::userSummary) ?: throw NoSuchElementException("User not found")

    @Transactional(readOnly = true)
    override fun authenticate(accessToken: String): BearerPrincipal? {
        val record = accessTokens[accessToken] ?: return null
        if (!clock.instant().isBefore(record.expiresAt)) {
            accessTokens.remove(accessToken, record)
            return null
        }
        return users.findUser(record.userId)
            ?.takeIf { it.status == UserStatus.ACTIVE }
            ?.let(::authenticatedPrincipal)
    }

    private fun createUserWithAccount(command: CompleteSocialLoginCommand): User {
        val user = users.saveUser(User())
        accounts.saveOAuthAccount(OAuthAccount(user, command.provider, command.providerSubject))
        return user
    }

    private fun resolveActiveUser(command: CompleteSocialLoginCommand): User {
        val account = accounts.findOAuthAccount(command.provider, command.providerSubject)
        val user = account?.user?.let { users.findUserForUpdate(it.id) }
            ?: if (account == null) createUserWithAccount(command) else throw AuthenticationRequiredException()
        if (user.status != UserStatus.ACTIVE) {
            throw AuthenticationRequiredException("User is not eligible to sign in")
        }
        return user
    }

    private fun authenticatedPrincipal(user: User): AuthenticatedUserPrincipal = when (user.role) {
        UserRole.USER -> AuthenticatedUserPrincipal(user.id)
        UserRole.ADMIN -> AuthenticatedUserPrincipal(
            user.id,
            AuthenticatedUserPrincipal.ADMIN_AUTHORITY,
        )
    }

    private fun issueTokens(user: User): IssuedSession {
        check(user.status == UserStatus.ACTIVE) { "Only an active user can start a session" }
        val issuedAt = clock.instant()
        val access = randomToken()
        val refresh = issueRefreshSession(user, issuedAt)
        accessTokens[access] = AccessTokenRecord(
            userId = user.id,
            sessionId = refresh.session.id,
            expiresAt = issuedAt.plus(Duration.ofMinutes(ACCESS_TOKEN_EXPIRY_MINUTES)),
        )
        return IssuedSession(
            tokens = AuthTokens(access, refresh.rawToken, userSummary(user)),
            session = refresh.session,
        )
    }

    private fun issueRefreshSession(user: User, issuedAt: Instant): IssuedRefreshSession {
        val rawToken = randomToken()
        val session = sessions.saveSession(
            UserSession(
                user = user,
                refreshTokenHash = hash(rawToken),
                expiresAt = issuedAt.plus(Duration.ofDays(REFRESH_TOKEN_EXPIRY_DAYS)),
            ),
        )
        return IssuedRefreshSession(rawToken, session)
    }

    private fun userSummary(user: User) = UserSummary(user.id, user.status.name, user.role)
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ACCESS_TOKEN_EXPIRY_MINUTES = 15L
        const val REFRESH_TOKEN_EXPIRY_DAYS = 30L
        const val MOBILE_GRANT_EXPIRY_MINUTES = 2L
    }
}

private data class AccessTokenRecord(
    val userId: Long,
    val sessionId: Long,
    val expiresAt: Instant,
)

private data class IssuedSession(
    val tokens: AuthTokens,
    val session: UserSession,
)

private data class IssuedRefreshSession(
    val rawToken: String,
    val session: UserSession,
)
