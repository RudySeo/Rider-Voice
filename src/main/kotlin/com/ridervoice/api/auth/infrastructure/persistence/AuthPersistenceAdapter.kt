package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.OnboardingTokenStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import org.springframework.stereotype.Component

@Component
class AuthPersistenceAdapter(
    private val users: UserRepository,
    private val accounts: OAuthAccountRepository,
    private val sessions: UserSessionRepository,
    private val onboardingTokens: OnboardingTokenRepository,
) : UserStore, OAuthAccountStore, UserSessionStore, OnboardingTokenStore {

    override fun findUser(userId: Long): User? = users.findById(userId).orElse(null)

    override fun saveUser(user: User): User = users.save(user)

    override fun findOAuthAccount(
        provider: OAuthProvider,
        providerSubject: String,
    ): OAuthAccount? = accounts.findByProviderAndProviderSubject(provider, providerSubject).orElse(null)

    override fun saveOAuthAccount(account: OAuthAccount): OAuthAccount = accounts.save(account)

    override fun findSessionForUpdate(refreshTokenHash: String): UserSession? =
        sessions.findByRefreshTokenHashForUpdate(refreshTokenHash).orElse(null)

    override fun saveSession(session: UserSession): UserSession = sessions.save(session)

    override fun findOnboardingToken(tokenHash: String): OnboardingToken? =
        onboardingTokens.findByTokenHash(tokenHash).orElse(null)

    override fun findOnboardingTokenForUpdate(tokenHash: String): OnboardingToken? =
        onboardingTokens.findByTokenHashForUpdate(tokenHash).orElse(null)

    override fun saveOnboardingToken(token: OnboardingToken): OnboardingToken = onboardingTokens.save(token)
}
