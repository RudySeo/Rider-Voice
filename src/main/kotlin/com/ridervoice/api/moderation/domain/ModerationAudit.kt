package com.ridervoice.api.moderation.domain

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "moderation_audits",
    indexes = [
        Index(
            name = "idx_moderation_audits_target_created",
            columnList = "target_type, target_id, created_at, id",
        ),
    ],
)
class ModerationAudit(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "actor_user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_moderation_audits_actor_user"),
    )
    val actor: User,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 48)
    val action: ModerationAuditAction,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "target_type", nullable = false, updatable = false, length = 32)
    val targetType: ModerationTargetType,
    @field:Column(name = "target_id", nullable = false, updatable = false)
    val targetId: Long,
    @field:Lob
    @field:Column(updatable = false, columnDefinition = "TEXT")
    val reason: String?,
    @field:Lob
    @field:Column(name = "before_state", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    val beforeState: String,
    @field:Lob
    @field:Column(name = "after_state", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    val afterState: String,
    @field:Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant,
) : BaseEntity() {

    init {
        require(targetId > 0) { "Moderation audit target ID must be positive" }
    }
}
