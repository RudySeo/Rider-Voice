package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.OAuthAccountStore
import com.ridervoice.api.auth.application.port.out.MobileLoginGrantStore
import com.ridervoice.api.auth.application.port.out.UserSessionStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.MobileLoginGrant
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import org.springframework.stereotype.Component

@Component
class AuthPersistenceAdapter(
    private val users: UserRepository,
    private val accounts: OAuthAccountRepository,
    private val sessions: UserSessionRepository,
    private val mobileLoginGrants: MobileLoginGrantRepository,
) : UserStore, OAuthAccountStore, UserSessionStore, MobileLoginGrantStore {

    override fun findUser(userId: Long): User? = users.findById(userId).orElse(null)

    override fun findUserForUpdate(userId: Long): User? = users.findByIdForUpdate(userId)

    override fun saveUser(user: User): User = users.save(user)

    override fun findOAuthAccount(
        provider: OAuthProvider,
        providerSubject: String,
    ): OAuthAccount? = accounts.findByProviderAndProviderSubject(provider, providerSubject).orElse(null)

    override fun saveOAuthAccount(account: OAuthAccount): OAuthAccount = accounts.save(account)

    override fun findSessionForUpdate(refreshTokenHash: String): UserSession? =
        sessions.findByRefreshTokenHashForUpdate(refreshTokenHash).orElse(null)

    override fun saveSession(session: UserSession): UserSession = sessions.save(session)

    override fun findGrantForUpdate(codeHash: String): MobileLoginGrant? =
        mobileLoginGrants.findByCodeHashForUpdate(codeHash).orElse(null)

    override fun saveGrant(grant: MobileLoginGrant): MobileLoginGrant = mobileLoginGrants.save(grant)
}
