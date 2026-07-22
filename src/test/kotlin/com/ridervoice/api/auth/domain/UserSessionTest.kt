package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserSessionTest {

    private val userId = UUID.randomUUID()
    private val expiresAt = Instant.parse("2026-07-23T12:00:00Z")

    @Test
    fun `session keeps only the refresh token hash`() {
        val session = UserSession(
            userId = userId,
            refreshTokenHash = "sha256:hashed-value",
            expiresAt = expiresAt,
        )

        assertThat(session.refreshTokenHash).isEqualTo("sha256:hashed-value")
        assertThat(UserSession::class.java.declaredFields.map { it.name })
            .doesNotContain("refreshToken")
    }

    @Test
    fun `rotation revokes the current session and records its successor`() {
        val session = UserSession(userId, "sha256:current", expiresAt)
        val successorId = UUID.randomUUID()
        val rotatedAt = expiresAt.minusSeconds(60)

        assertThat(session.isActiveAt(rotatedAt)).isTrue()

        session.rotateTo(successorId, rotatedAt)

        assertThat(session.isActiveAt(rotatedAt)).isFalse()
        assertThat(session.revokedAt).isEqualTo(rotatedAt)
        assertThat(session.rotatedToSessionId).isEqualTo(successorId)
        assertThatIllegalStateException()
            .isThrownBy { session.rotateTo(UUID.randomUUID(), rotatedAt) }
    }

    @Test
    fun `session cannot rotate to itself or after expiry`() {
        val session = UserSession(userId, "sha256:current", expiresAt)

        assertThatIllegalArgumentException()
            .isThrownBy { session.rotateTo(session.id, expiresAt.minusSeconds(1)) }
        assertThatIllegalStateException()
            .isThrownBy { session.rotateTo(UUID.randomUUID(), expiresAt) }
    }
}
