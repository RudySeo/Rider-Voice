package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.auth.infrastructure.persistence.UserRepository
import com.ridervoice.api.auth.infrastructure.persistence.UserSessionRepository
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class SessionLifecycleIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var auth: AuthService

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var sessions: UserSessionRepository

    @Test
    fun `OAuth callback completion creates an active user and refresh session`() {
        val subject = "pending-${UUID.randomUUID()}"
        val login = auth.complete(CompleteSocialLoginCommand(OAuthProvider.KAKAO, subject))
        val result = auth.refresh(RefreshSessionCommand(login.refreshToken))

        val persistedUser = users.findById(result.user.id).orElseThrow()
        assertThat(persistedUser.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(result.accessToken).isNotBlank()
        assertThat(result.refreshToken).isNotBlank()
        assertThat(sessions.findAll().count { it.user.id == persistedUser.id }).isEqualTo(2)
        assertThat(auth.authenticate(result.accessToken)).isNotNull()
    }

    @Test
    fun `concurrent refresh creates exactly one successor session`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val user = User()
        users.saveAndFlush(user)
        val rawRefreshToken = "refresh-${UUID.randomUUID()}"
        val original = sessions.saveAndFlush(
            UserSession(
                user = user,
                refreshTokenHash = sha256(rawRefreshToken),
                expiresAt = now.plus(30, ChronoUnit.DAYS),
            ),
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val futures = (1..2).map {
            executor.submit<Result<AuthTokens>> {
                ready.countDown()
                start.await()
                runCatching { auth.refresh(RefreshSessionCommand(rawRefreshToken)) }
            }
        }
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(1)
        val persistedOriginal = sessions.findById(original.id).orElseThrow()
        val persistedForUser = sessions.findAll().filter { it.user.id == user.id }
        assertThat(persistedForUser).hasSize(2)
        assertThat(persistedOriginal.revokedAt).isNotNull()
        assertThat(persistedOriginal.rotatedToSession?.id)
            .isEqualTo(persistedForUser.single { it.id != original.id }.id)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
