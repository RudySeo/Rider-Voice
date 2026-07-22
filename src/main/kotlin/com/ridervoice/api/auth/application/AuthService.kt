package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthLoginState
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.infrastructure.persistence.OAuthAccountRepository
import com.ridervoice.api.auth.infrastructure.persistence.OAuthLoginStateRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserSessionRepository
import com.ridervoice.api.common.error.AuthenticationRequiredException
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
    private val clock: Clock = Clock.systemUTC(),
) {
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
        return CallbackResult(userSummary(user), user.status.name == "ACTIVE", if (user.status.name == "ACTIVE") issueTokens(user) else null)
    }

    @Transactional
    fun agree(userId: UUID, version: String): AuthTokens {
        val user = users.findById(userId).orElseThrow()
        user.agreeToTerms(version, clock.instant())
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
    fun logout(refreshToken: String) { sessions.findByRefreshTokenHash(hash(refreshToken)).ifPresent { it.revoke(clock.instant()) } }

    fun me(accessToken: String): UserSummary = users.findById(accessUsers[accessToken] ?: throw AuthenticationRequiredException()).map(::userSummary).orElseThrow()
    fun userIdFor(accessToken: String): UUID = accessUsers[accessToken] ?: throw AuthenticationRequiredException()

    private fun issueTokens(user: User): AuthTokens {
        val access = randomToken(); val refresh = randomToken()
        accessUsers[access] = user.id
        sessions.save(UserSession(user.id, hash(refresh), clock.instant().plus(Duration.ofDays(30))))
        return AuthTokens(access, refresh, userSummary(user))
    }
    private fun userSummary(user: User) = UserSummary(user.id, user.status.name, user.termsVersion)
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class CallbackResult(val user: UserSummary, val termsAgreed: Boolean, val tokens: AuthTokens?)
