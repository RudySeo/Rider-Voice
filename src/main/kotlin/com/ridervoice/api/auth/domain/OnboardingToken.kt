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
    name = "onboarding_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_onboarding_tokens_token_hash", columnNames = ["token_hash"]),
    ],
)
class OnboardingToken(
    @field:Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @field:Column(name = "token_hash", nullable = false, updatable = false, length = 255)
    val tokenHash: String,
    issuedAt: Instant,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
    @field:Id
    @field:Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    val issuedAt: Instant
        get() = createdAt

    @field:Column(name = "consumed_at")
    final var consumedAt: Instant? = null
        private set

    init {
        require(tokenHash.isNotBlank()) { "Onboarding token hash must not be blank" }
        require(expiresAt == issuedAt.plusSeconds(EXPIRY_SECONDS)) {
            "Onboarding token must expire five minutes after issuance"
        }
        createdAt = issuedAt
        updatedAt = issuedAt
    }

    fun isUsableAt(at: Instant): Boolean = consumedAt == null && at.isBefore(expiresAt)

    @Synchronized
    fun consume(at: Instant) {
        check(isUsableAt(at)) { "Onboarding token is consumed or expired" }
        consumedAt = at
    }

    private companion object {
        const val EXPIRY_SECONDS = 5 * 60L
    }
}
