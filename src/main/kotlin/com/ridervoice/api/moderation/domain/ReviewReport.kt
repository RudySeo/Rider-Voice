package com.ridervoice.api.moderation.domain

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.review.domain.Review
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
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "review_reports",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_reports_reporter_review",
            columnNames = ["reporter_user_id", "review_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_review_reports_status_created",
            columnList = "status, created_at, id",
        ),
    ],
)
class ReviewReport(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "reporter_user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_review_reports_reporter_user"),
    )
    val reporter: User,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "review_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_review_reports_review"),
    )
    val review: Review,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 40)
    val reason: ReviewReportReason,
    @field:Lob
    @field:Column(updatable = false)
    val details: String?,
) : BaseEntity() {

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    final var status: ReportStatus = ReportStatus.PENDING
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(length = 32)
    final var decision: ReviewReportDecision? = null
        private set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(
        name = "decided_by_user_id",
        foreignKey = ForeignKey(name = "fk_review_reports_decided_by_user"),
    )
    final var decidedBy: User? = null
        private set

    @field:Column(name = "decided_at")
    final var decidedAt: Instant? = null
        private set

    fun resolve(decision: ReviewReportDecision, decidedBy: User, decidedAt: Instant) {
        check(status == ReportStatus.PENDING) { "Only a pending review report can be resolved" }
        status = ReportStatus.RESOLVED
        this.decision = decision
        this.decidedBy = decidedBy
        this.decidedAt = decidedAt
    }
}
