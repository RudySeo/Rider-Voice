package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "user_sessions",
    indexes = [
        Index(name = "idx_user_sessions_user", columnList = "user_id"),
        Index(name = "idx_user_sessions_active_expiry", columnList = "revoked_at, expires_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_sessions_refresh_token_hash", columnNames = ["refresh_token_hash"]),
    ],
)
class UserSession(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,
    @field:Column(name = "refresh_token_hash", nullable = false, updatable = false, length = 255)
    val refreshTokenHash: String,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
) : BaseEntity() {

    @field:Column(name = "revoked_at")
    final var revokedAt: Instant? = null
        private set

    @field:OneToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "rotated_to_session_id")
    final var rotatedToSession: UserSession? = null
        private set

    init {
        require(refreshTokenHash.isNotBlank()) { "Refresh token hash must not be blank" }
    }

    fun isActiveAt(at: Instant): Boolean = revokedAt == null && at.isBefore(expiresAt)

    fun revoke(revokedAt: Instant) {
        check(this.revokedAt == null) { "Session has already been revoked" }
        this.revokedAt = revokedAt
    }

    fun rotateTo(successorSession: UserSession, rotatedAt: Instant) {
        require(successorSession !== this) { "Session cannot rotate to itself" }
        check(isActiveAt(rotatedAt)) { "Only an active session can be rotated" }

        rotatedToSession = successorSession
        revokedAt = rotatedAt
    }
}
