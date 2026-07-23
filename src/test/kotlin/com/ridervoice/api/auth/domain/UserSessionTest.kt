package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant

class UserSessionTest {

    private val user = User().apply { id = 1L }
    private val expiresAt = Instant.parse("2026-07-23T12:00:00Z")

    @Test
    fun `session keeps only the refresh token hash`() {
        val session = UserSession(
            user = user,
            refreshTokenHash = "sha256:hashed-value",
            expiresAt = expiresAt,
        )

        assertThat(session.refreshTokenHash).isEqualTo("sha256:hashed-value")
        assertThat(UserSession::class.java.declaredFields.map { it.name })
            .doesNotContain("refreshToken")
    }

    @Test
    fun `rotation revokes the current session and records its successor`() {
        val session = UserSession(user, "sha256:current", expiresAt).apply { id = 10L }
        val successor = UserSession(user, "sha256:next", expiresAt.plusSeconds(60)).apply { id = 11L }
        val rotatedAt = expiresAt.minusSeconds(60)

        assertThat(session.isActiveAt(rotatedAt)).isTrue()

        session.rotateTo(successor, rotatedAt)

        assertThat(session.isActiveAt(rotatedAt)).isFalse()
        assertThat(session.revokedAt).isEqualTo(rotatedAt)
        assertThat(session.rotatedToSession).isSameAs(successor)
        assertThatIllegalStateException()
            .isThrownBy { session.rotateTo(UserSession(user, "sha256:another", expiresAt), rotatedAt) }
    }

    @Test
    fun `session cannot rotate to itself or after expiry`() {
        val session = UserSession(user, "sha256:current", expiresAt)

        assertThatIllegalArgumentException()
            .isThrownBy { session.rotateTo(session, expiresAt.minusSeconds(1)) }
        assertThatIllegalStateException()
            .isThrownBy { session.rotateTo(UserSession(user, "sha256:next", expiresAt), expiresAt) }
    }
}
