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
import com.ridervoice.api.common.error.StateConflictException
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AuthTokens(val accessToken: String, val refreshToken: String, val user: UserSummary)
data class UserSummary(val id: UUID, val status: String, val termsVersion: String?)

@Service
class AuthService(
    private val kakao: KakaoOAuthPort,
    private val users: UserRepository,
    private val accounts: OAuthAccountRepository,
    private val states: OAuthLoginStateRepository,
    private val sessions: UserSessionRepository,
    private val onboardingTokens: OnboardingTokenRepository,
    private val clock: Clock = Clock.systemUTC(),
) : AccessTokenAuthenticator {
    private val accessUsers = ConcurrentHashMap<String, UUID>()
    private val random = SecureRandom()

    @Transactional
    fun authorize(): String {
        val state = randomToken()
        states.save(OAuthLoginState(hash(state), clock.instant().plusSeconds(300)))
        return kakao.authorizationUri(state)
    }

    @Transactional
    fun callback(code: String, state: String): CallbackResult {
        val loginState = states.findByStateHash(hash(state)).orElseThrow { IllegalArgumentException("Invalid OAuth state") }
        loginState.consume(clock.instant())
        val profile = kakao.getUser(kakao.exchangeCode(code))
        val account = accounts.findByProviderAndProviderSubject(OAuthProvider.KAKAO, profile.providerSubject).orElse(null)
        val user = account?.let { users.findById(it.userId).orElseThrow() } ?: users.save(User().also { accounts.save(OAuthAccount(it.id, OAuthProvider.KAKAO, profile.providerSubject)) })
        return when (user.status) {
            UserStatus.ACTIVE -> CallbackResult(
                user = userSummary(user),
                termsAgreed = true,
                tokens = issueTokens(user),
                onboardingToken = null,
            )
            UserStatus.PENDING_TERMS -> CallbackResult(
                user = userSummary(user),
                termsAgreed = false,
                tokens = null,
                onboardingToken = issueOnboardingToken(user),
            )
            else -> throw StateConflictException("User is not eligible to start a session")
        }
    }

    @Transactional
    fun agree(principal: OnboardingPrincipal, version: String): AuthTokens {
        if (principal.tokenHash.isBlank()) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val token = onboardingTokens.findByTokenHashForUpdate(principal.tokenHash)
            .orElseThrow { AuthenticationRequiredException("Invalid onboarding token") }
        val now = clock.instant()
        if (token.userId != principal.userId || !token.isUsableAt(now)) {
            throw AuthenticationRequiredException("Invalid onboarding token")
        }
        val user = users.findById(principal.userId).orElseThrow()
        token.consume(now)
        user.agreeToTerms(version, now)
        return issueTokens(user)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthTokens {
        val session = sessions.findByRefreshTokenHash(hash(refreshToken)).orElseThrow { IllegalArgumentException("Invalid refresh token") }
        check(session.isActiveAt(clock.instant())) { "Refresh session is inactive" }
        val user = users.findById(session.userId).orElseThrow()
        val next = issueTokens(user)
        session.rotateTo(sessions.findByRefreshTokenHash(hash(next.refreshToken)).orElseThrow().id, clock.instant())
        return next
    }

    @Transactional
    fun logout(principal: AuthenticatedUserPrincipal, refreshToken: String) {
        sessions.findByRefreshTokenHash(hash(refreshToken))
            .filter { it.userId == principal.userId }
            .ifPresent { it.revoke(clock.instant()) }
    }

    fun me(principal: AuthenticatedUserPrincipal): UserSummary = users.findById(principal.userId).map(::userSummary).orElseThrow()

    override fun authenticate(accessToken: String): BearerPrincipal? {
        accessUsers[accessToken]?.let { return AuthenticatedUserPrincipal(it) }
        return onboardingTokens.findByTokenHash(hash(accessToken))
            .filter { it.isUsableAt(clock.instant()) }
            .map { OnboardingPrincipal(it.userId, it.tokenHash) }
            .orElse(null)
    }

    private fun issueOnboardingToken(user: User): String {
        val rawToken = randomToken()
        val issuedAt = clock.instant()
        onboardingTokens.save(
            OnboardingToken(
                userId = user.id,
                tokenHash = hash(rawToken),
                issuedAt = issuedAt,
                expiresAt = issuedAt.plusSeconds(ONBOARDING_EXPIRY_SECONDS),
            ),
        )
        return rawToken
    }

    private fun issueTokens(user: User): AuthTokens {
        val access = randomToken(); val refresh = randomToken()
        accessUsers[access] = user.id
        sessions.save(UserSession(user.id, hash(refresh), clock.instant().plus(Duration.ofDays(30))))
        return AuthTokens(access, refresh, userSummary(user))
    }
    private fun userSummary(user: User) = UserSummary(user.id, user.status.name, user.termsVersion)
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ONBOARDING_EXPIRY_SECONDS = 5 * 60L
    }
}

data class CallbackResult(
    val user: UserSummary,
    val termsAgreed: Boolean,
    val tokens: AuthTokens?,
    val onboardingToken: String?,
)
