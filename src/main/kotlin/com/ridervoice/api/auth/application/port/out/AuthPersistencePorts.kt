package com.ridervoice.api.auth.application.port.out

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.MobileLoginGrant
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.RiderInviteCode
import com.ridervoice.api.auth.domain.RiderVerificationAttempt

interface UserStore {
    fun findUser(userId: Long): User?
    fun findUserForUpdate(userId: Long): User?
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

interface MobileLoginGrantStore {
    fun findGrantForUpdate(codeHash: String): MobileLoginGrant?
    fun saveGrant(grant: MobileLoginGrant): MobileLoginGrant
}

interface RiderInviteCodeStore {
    fun findCurrentForUpdate(): RiderInviteCode?
    fun saveCode(code: RiderInviteCode): RiderInviteCode
}

interface RiderVerificationAttemptStore {
    fun findByUserIdForUpdate(userId: Long): RiderVerificationAttempt?
    fun saveAttempt(attempt: RiderVerificationAttempt): RiderVerificationAttempt
}

interface RiderCodeHasher {
    fun hash(rawCode: String): String
    fun matches(rawCode: String, encodedCode: String): Boolean
}
