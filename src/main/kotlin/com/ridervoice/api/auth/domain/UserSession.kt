package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_sessions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_sessions_refresh_token_hash", columnNames = ["refresh_token_hash"]),
    ],
)
class UserSession(
    @field:Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @field:Column(name = "refresh_token_hash", nullable = false, updatable = false, length = 255)
    val refreshTokenHash: String,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
    @field:Id
    @field:Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    @field:Column(name = "revoked_at")
    final var revokedAt: Instant? = null
        private set

    @field:Column(name = "rotated_to_session_id")
    final var rotatedToSessionId: UUID? = null
        private set

    init {
        require(refreshTokenHash.isNotBlank()) { "Refresh token hash must not be blank" }
    }

    fun isActiveAt(at: Instant): Boolean = revokedAt == null && at.isBefore(expiresAt)

    fun revoke(revokedAt: Instant) {
        check(this.revokedAt == null) { "Session has already been revoked" }
        this.revokedAt = revokedAt
    }

    fun rotateTo(successorSessionId: UUID, rotatedAt: Instant) {
        require(successorSessionId != id) { "Session cannot rotate to itself" }
        check(isActiveAt(rotatedAt)) { "Only an active session can be rotated" }

        rotatedToSessionId = successorSessionId
        revokedAt = rotatedAt
    }
}
