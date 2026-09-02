package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Index
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "rider_invite_codes",
    uniqueConstraints = [UniqueConstraint(name = "uk_rider_invite_codes_current_slot", columnNames = ["current_slot"])],
    indexes = [Index(name = "idx_rider_invite_codes_created", columnList = "created_at,id")],
)
class RiderInviteCode(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "rotated_by_user_id", nullable = false)
    val rotatedBy: User,

    @field:Column(name = "code_hash", nullable = false, length = 60)
    val codeHash: String,
) : BaseEntity() {

    @field:Column(name = "current_slot")
    final var currentSlot: Int? = CURRENT_SLOT
        private set

    @field:Column(name = "revoked_at")
    final var revokedAt: Instant? = null
        private set

    val isCurrent: Boolean
        get() = currentSlot == CURRENT_SLOT && revokedAt == null

    fun revoke(at: Instant) {
        if (!isCurrent) return
        currentSlot = null
        revokedAt = at
    }

    private companion object {
        const val CURRENT_SLOT = 1
    }
}
