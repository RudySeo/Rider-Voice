package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserSession
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
    fun `concurrent refresh creates exactly one successor session`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val user = User().also { it.agreeToTerms("2026-07-01", now) }
        users.saveAndFlush(user)
        val rawRefreshToken = "refresh-${UUID.randomUUID()}"
        val original = sessions.saveAndFlush(
            UserSession(
                userId = user.id,
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
                runCatching { auth.refresh(rawRefreshToken) }
            }
        }
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(1)
        val persistedOriginal = sessions.findById(original.id).orElseThrow()
        val persistedForUser = sessions.findAll().filter { it.userId == user.id }
        assertThat(persistedForUser).hasSize(2)
        assertThat(persistedOriginal.revokedAt).isNotNull()
        assertThat(persistedOriginal.rotatedToSessionId)
            .isEqualTo(persistedForUser.single { it.id != original.id }.id)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
