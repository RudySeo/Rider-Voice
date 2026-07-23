package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "onboarding_tokens",
    indexes = [
        Index(
            name = "idx_onboarding_tokens_active_expiry",
            columnList = "consumed_at, expires_at",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_onboarding_tokens_token_hash", columnNames = ["token_hash"]),
    ],
)
class OnboardingToken(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,
    @field:Column(name = "token_hash", nullable = false, updatable = false, length = 255)
    val tokenHash: String,
    issuedAt: Instant,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
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
