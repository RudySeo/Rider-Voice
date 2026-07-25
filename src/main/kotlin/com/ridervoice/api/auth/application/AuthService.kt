package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.auth.infrastructure.persistence.OnboardingTokenRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserSessionRepository
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
data class UserSummary(val id: Long, val status: String, val termsVersion: String?)

@Service
class AuthService(
    private val users: UserRepository,
    private val sessions: UserSessionRepository,
    private val onboardingTokens: OnboardingTokenRepository,
    private val clock: Clock = Clock.systemUTC(),
) : AccessTokenAuthenticator {
    private val accessTokens = ConcurrentHashMap<String, AccessTokenRecord>()
    private val random = SecureRandom()

    @Transactional
    fun agree(principal: OnboardingPrincipal, version: String): AuthTokens {
        if (principal.tokenHash.isBlank()) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val token = onboardingTokens.findByTokenHashForUpdate(principal.tokenHash)
            .orElseThrow { AuthenticationRequiredException("Invalid onboarding token") }
        val now = clock.instant()
        if (token.user.id != principal.userId || !token.isUsableAt(now)) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val user = users.findById(principal.userId).orElseThrow()
        token.consume(now)
        user.agreeToTerms(version, now)
        return issueTokens(user).tokens
    }

    @Transactional
    fun refresh(refreshToken: String): AuthTokens {
        val session = sessions.findByRefreshTokenHashForUpdate(hash(refreshToken))
            .orElseThrow { IllegalArgumentException("Invalid refresh token") }
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
        sessions.findByRefreshTokenHashForUpdate(hash(refreshToken))
            .filter { it.user.id == principal.userId }
            .ifPresent { session ->
                session.revoke(clock.instant())
                accessTokens.entries.removeIf { it.value.sessionId == session.id }
            }
    }

    fun me(principal: AuthenticatedUserPrincipal): UserSummary = users.findById(principal.userId).map(::userSummary).orElseThrow()

    @Transactional(readOnly = true)
    override fun authenticate(accessToken: String): BearerPrincipal? {
        accessTokens[accessToken]?.let { record ->
            if (!clock.instant().isBefore(record.expiresAt)) {
                accessTokens.remove(accessToken, record)
                return null
            }
            return users.findById(record.userId)
                .filter { it.status == UserStatus.ACTIVE }
                .map { AuthenticatedUserPrincipal(it.id) }
                .orElse(null)
        }
        return onboardingTokens.findByTokenHash(hash(accessToken))
            .filter { it.isUsableAt(clock.instant()) }
            .map { OnboardingPrincipal(it.user.id, it.tokenHash) }
            .orElse(null)
    }

    private fun issueOnboardingToken(user: User): String {
        val rawToken = randomToken()
        val issuedAt = clock.instant()
        onboardingTokens.save(
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
        val session = sessions.save(
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

    private fun userSummary(user: User) = UserSummary(user.id, user.status.name, user.termsVersion)
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
