package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@Transactional
class AuthPersistenceIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var oauthAccounts: OAuthAccountRepository

    @Autowired
    private lateinit var userSessions: UserSessionRepository

    @Test
    fun `auth repositories persist and find provider account and session data`() {
        val user = users.save(User())
        val account = oauthAccounts.save(OAuthAccount(user, OAuthProvider.KAKAO, "provider-subject"))
        val session = userSessions.save(
            UserSession(
                user = user,
                refreshTokenHash = "sha256:refresh-token-hash",
                expiresAt = Instant.parse("2026-07-23T12:00:00Z"),
            ),
        )
        assertThat(oauthAccounts.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "provider-subject"))
            .contains(account)
        assertThat(userSessions.findByRefreshTokenHash("sha256:refresh-token-hash"))
            .contains(session)
    }
}
