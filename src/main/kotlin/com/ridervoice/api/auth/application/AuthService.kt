package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.ServiceTokens
import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.OnboardingTokenStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.BearerPrincipal
import com.ridervoice.api.common.security.OnboardingPrincipal
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
    private val onboardingTokens: OnboardingTokenStore,
    private val clock: Clock = Clock.systemUTC(),
) : CompleteSocialLoginUseCase, AccessTokenAuthenticator {
    private val accessTokens = ConcurrentHashMap<String, AccessTokenRecord>()
    private val random = SecureRandom()

    @Transactional
    override fun complete(command: CompleteSocialLoginCommand): CompleteSocialLoginResult {
        val user = accounts.findOAuthAccount(command.provider, command.providerSubject)?.user
            ?: createUserWithAccount(command)

        return when (user.status) {
            UserStatus.PENDING_TERMS -> CompleteSocialLoginResult(
                user = userSummary(user),
                termsAgreed = false,
                onboardingToken = issueOnboardingToken(user),
                tokens = null,
            )

            UserStatus.ACTIVE -> {
                val issued = issueTokens(user).tokens
                CompleteSocialLoginResult(
                    user = userSummary(user),
                    termsAgreed = true,
                    onboardingToken = null,
                    tokens = ServiceTokens(issued.accessToken, issued.refreshToken),
                )
            }

            else -> throw AuthenticationRequiredException("User is not eligible to sign in")
        }
    }

    @Transactional
    fun agree(principal: OnboardingPrincipal, version: String): AuthTokens {
        if (principal.tokenHash.isBlank()) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val token = onboardingTokens.findOnboardingTokenForUpdate(principal.tokenHash)
            ?: throw AuthenticationRequiredException("Invalid onboarding token")
        val now = clock.instant()
        if (token.user.id != principal.userId || !token.isUsableAt(now)) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val user = users.findUser(principal.userId) ?: throw NoSuchElementException("User not found")
        token.consume(now)
        user.agreeToTerms(version, now)
        return issueTokens(user).tokens
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
        accessTokens[accessToken]?.let { record ->
            if (!clock.instant().isBefore(record.expiresAt)) {
                accessTokens.remove(accessToken, record)
                return null
            }
            return users.findUser(record.userId)
                ?.takeIf { it.status == UserStatus.ACTIVE }
                ?.let(::authenticatedPrincipal)
        }
        return onboardingTokens.findOnboardingToken(hash(accessToken))
            ?.takeIf { it.isUsableAt(clock.instant()) }
            ?.let { OnboardingPrincipal(it.user.id, it.tokenHash) }
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

    private fun issueOnboardingToken(user: User): String {
        val rawToken = randomToken()
        val issuedAt = clock.instant()
        onboardingTokens.saveOnboardingToken(
            OnboardingToken(
                user = user,
                tokenHash = hash(rawToken),
                issuedAt = issuedAt,
                expiresAt = issuedAt.plusSeconds(ONBOARDING_EXPIRY_SECONDS),
            ),
        )
        return rawToken
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
        const val ONBOARDING_EXPIRY_SECONDS = 5 * 60L
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
