package com.ridervoice.api.moderation.application.port.out

import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import java.time.Instant

data class ModerationCursor(
    val createdAt: Instant,
    val id: Long,
) {
    init {
        require(id > 0) { "Moderation cursor ID must be positive" }
    }
}

interface ReviewReportRepository {
    fun create(command: NewReviewReportPersistenceCommand): StoredReviewReport
    fun existsByReporterUserIdAndReviewId(reporterUserId: Long, reviewId: Long): Boolean
    fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long
    fun findPending(cursor: ModerationCursor?, limit: Int): List<StoredReviewReport>
    fun findPendingForUpdate(reportId: Long): StoredReviewReport?
    fun saveDecision(command: ReviewReportDecisionPersistenceCommand): StoredReviewReport
}

data class NewReviewReportPersistenceCommand(
    val reporterUserId: Long,
    val reviewId: Long,
    val reason: ReviewReportReason,
    val details: String?,
) {
    init {
        require(reporterUserId > 0) { "Reporter user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

data class ReviewReportDecisionPersistenceCommand(
    val reportId: Long,
    val decision: ReviewReportDecision,
    val decidedByUserId: Long,
    val decidedAt: Instant,
) {
    init {
        require(reportId > 0) { "Review report ID must be positive" }
        require(decidedByUserId > 0) { "Decision actor user ID must be positive" }
    }
}

data class StoredReviewReport(
    val reportId: Long,
    val reporterUserId: Long,
    val reviewId: Long,
    val reason: ReviewReportReason,
    val details: String?,
    val status: ReportStatus,
    val decision: ReviewReportDecision?,
    val decidedByUserId: Long?,
    val decidedAt: Instant?,
    val createdAt: Instant,
)

interface RestaurantInfoReportRepository {
    fun create(command: NewRestaurantInfoReportPersistenceCommand): StoredRestaurantInfoReport
    fun existsByReporterUserIdAndRestaurantId(reporterUserId: Long, restaurantId: Long): Boolean
    fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long
    fun findPending(cursor: ModerationCursor?, limit: Int): List<StoredRestaurantInfoReport>
    fun findPendingForUpdate(reportId: Long): StoredRestaurantInfoReport?
    fun saveDecision(command: RestaurantInfoReportDecisionPersistenceCommand): StoredRestaurantInfoReport
}

data class NewRestaurantInfoReportPersistenceCommand(
    val reporterUserId: Long,
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val details: String?,
) {
    init {
        require(reporterUserId > 0) { "Reporter user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}

data class RestaurantInfoReportDecisionPersistenceCommand(
    val reportId: Long,
    val decision: RestaurantInfoReportDecision,
    val decidedByUserId: Long,
    val decidedAt: Instant,
) {
    init {
        require(reportId > 0) { "Restaurant information report ID must be positive" }
        require(decidedByUserId > 0) { "Decision actor user ID must be positive" }
    }
}

data class StoredRestaurantInfoReport(
    val reportId: Long,
    val reporterUserId: Long,
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val details: String?,
    val status: ReportStatus,
    val decision: RestaurantInfoReportDecision?,
    val decidedByUserId: Long?,
    val decidedAt: Instant?,
    val createdAt: Instant,
)

fun interface ModerationAuditRepository {
    fun append(command: ModerationAuditPersistenceCommand): StoredModerationAudit
}

data class ModerationAuditPersistenceCommand(
    val actorUserId: Long,
    val action: ModerationAuditAction,
    val targetType: ModerationTargetType,
    val targetId: Long,
    val reason: String?,
    val beforeState: String,
    val afterState: String,
    val occurredAt: Instant,
) {
    init {
        require(actorUserId > 0) { "Moderation actor user ID must be positive" }
        require(targetId > 0) { "Moderation target ID must be positive" }
    }
}

data class StoredModerationAudit(
    val auditId: Long,
    val actorUserId: Long,
    val action: ModerationAuditAction,
    val targetType: ModerationTargetType,
    val targetId: Long,
    val reason: String?,
    val beforeState: String,
    val afterState: String,
    val occurredAt: Instant,
    val createdAt: Instant,
)
