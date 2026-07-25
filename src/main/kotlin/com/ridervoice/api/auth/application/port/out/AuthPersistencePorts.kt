package com.ridervoice.api.auth.application.port.out

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.OnboardingToken
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession

interface UserStore {
    fun findUser(userId: Long): User?
    fun saveUser(user: User): User
}

interface OAuthAccountStore {
    fun findOAuthAccount(provider: OAuthProvider, providerSubject: String): OAuthAccount?
    fun saveOAuthAccount(account: OAuthAccount): OAuthAccount
}

interface UserSessionStore {
    fun findSessionForUpdate(refreshTokenHash: String): UserSession?
    fun saveSession(session: UserSession): UserSession
}

interface OnboardingTokenStore {
    fun findOnboardingToken(tokenHash: String): OnboardingToken?
    fun findOnboardingTokenForUpdate(tokenHash: String): OnboardingToken?
    fun saveOnboardingToken(token: OnboardingToken): OnboardingToken
}
