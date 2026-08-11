package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.application.port.`in`.CompleteProviderLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeCommand
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeUseCase
import com.ridervoice.api.auth.application.port.`in`.ProviderLoginResult
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrant
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrantStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.error.InvalidOAuthExchangeCodeException
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
    val termsVersion: String?,
)

@Service
class AuthService(
    private val users: UserStore,
    private val accounts: OAuthAccountStore,
    private val sessions: UserSessionStore,
    private val exchangeGrants: OAuthExchangeGrantStore,
    private val clock: Clock = Clock.systemUTC(),
) : CompleteProviderLoginUseCase, ExchangeSocialLoginCodeUseCase, AccessTokenAuthenticator {
    private val accessTokens = ConcurrentHashMap<String, AccessTokenRecord>()
    private val random = SecureRandom()

    @Transactional
    override fun complete(command: CompleteSocialLoginCommand): ProviderLoginResult {
        val user = accounts.findOAuthAccount(command.provider, command.providerSubject)?.user
            ?: createUserWithAccount(command)
        val rawCode = randomToken()
        val issuedAt = clock.instant()
        exchangeGrants.save(
            hash(rawCode),
            OAuthExchangeGrant(
                userId = user.id,
                expiresAt = issuedAt.plusSeconds(OAUTH_EXCHANGE_EXPIRY_SECONDS),
            ),
        )
        return ProviderLoginResult(rawCode)
    }

    @Transactional
    override fun exchange(command: ExchangeSocialLoginCodeCommand): CompleteSocialLoginResult {
        val now = clock.instant()
        val grant = exchangeGrants.consume(hash(command.code), now)
            ?: throw InvalidOAuthExchangeCodeException()
        val user = users.findUserForUpdate(grant.userId)
            ?: throw InvalidOAuthExchangeCodeException()
        if (user.status == UserStatus.PENDING_TERMS) {
            user.agreeToTerms(CURRENT_TERMS_VERSION, now)
        }
        if (user.status != UserStatus.ACTIVE) {
            throw AuthenticationRequiredException("User is not eligible to sign in")
        }
        val tokens = issueTokens(user).tokens
        return CompleteSocialLoginResult(
            user = tokens.user,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    @Transactional
    fun refresh(refreshToken: String): AuthTokens {
        val session = sessions.findSessionForUpdate(hash(refreshToken))
            ?: throw IllegalArgumentException("Invalid refresh token")
        val now = clock.instant()
        check(session.isActiveAt(now)) { "Refresh session is inactive" }
        val user = session.user
        check(user.status == UserStatus.ACTIVE) { "User is not eligible to refresh a session" }
        val next = issueTokens(user)
        session.rotateTo(next.session, now)
        return next.tokens
    }

    @Transactional
    fun logout(principal: AuthenticatedUserPrincipal, refreshToken: String) {
        sessions.findSessionForUpdate(hash(refreshToken))
            ?.takeIf { it.user.id == principal.userId }
            ?.let { session ->
                session.revoke(clock.instant())
                accessTokens.entries.removeIf { it.value.sessionId == session.id }
            }
    }

    fun me(principal: AuthenticatedUserPrincipal): UserSummary =
        users.findUser(principal.userId)?.let(::userSummary) ?: throw NoSuchElementException("User not found")

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
        val refresh = randomToken()
        val session = sessions.saveSession(
            UserSession(
                user = user,
                refreshTokenHash = hash(refresh),
                expiresAt = issuedAt.plus(Duration.ofDays(REFRESH_TOKEN_EXPIRY_DAYS)),
            ),
        )
        accessTokens[access] = AccessTokenRecord(
            userId = user.id,
            sessionId = session.id,
            expiresAt = issuedAt.plus(Duration.ofMinutes(ACCESS_TOKEN_EXPIRY_MINUTES)),
        )
        return IssuedSession(
            tokens = AuthTokens(access, refresh, userSummary(user)),
            session = session,
        )
    }

    private fun userSummary(user: User) = UserSummary(user.id, user.status.name, user.role, user.termsVersion)
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CURRENT_TERMS_VERSION = "2026-07-01"
        const val OAUTH_EXCHANGE_EXPIRY_SECONDS = 60L
        const val ACCESS_TOKEN_EXPIRY_MINUTES = 15L
        const val REFRESH_TOKEN_EXPIRY_DAYS = 30L
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
