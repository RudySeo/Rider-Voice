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
    name = "oauth_login_states",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_oauth_login_states_state_hash", columnNames = ["state_hash"]),
    ],
)
class OAuthLoginState(
    @field:Column(name = "state_hash", nullable = false, updatable = false, length = 255)
    val stateHash: String,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
    @field:Id
    @field:Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    @field:Column(name = "consumed_at")
    final var consumedAt: Instant? = null
        private set

    init {
        require(stateHash.isNotBlank()) { "OAuth state hash must not be blank" }
    }

    fun isUsableAt(at: Instant): Boolean = consumedAt == null && at.isBefore(expiresAt)

    fun consume(consumedAt: Instant) {
        check(isUsableAt(consumedAt)) { "OAuth login state is consumed or expired" }
        this.consumedAt = consumedAt
    }
}
