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
    name = "mobile_login_grants",
    indexes = [Index(name = "idx_mobile_login_grants_expiry", columnList = "expires_at, consumed_at")],
    uniqueConstraints = [UniqueConstraint(name = "uk_mobile_login_grants_code_hash", columnNames = ["code_hash"])],
)
class MobileLoginGrant(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,
    @field:Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    val codeHash: String,
    @field:Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
) : BaseEntity() {
    @field:Column(name = "consumed_at")
    var consumedAt: Instant? = null
        protected set

    init {
        require(codeHash.isNotBlank()) { "Code hash must not be blank" }
    }

    fun consume(at: Instant): Boolean {
        if (consumedAt != null || !at.isBefore(expiresAt)) return false
        consumedAt = at
        return true
    }
}
