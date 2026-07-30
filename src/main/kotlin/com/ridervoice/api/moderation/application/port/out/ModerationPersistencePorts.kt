package com.ridervoice.api.moderation.application.port.out

import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import java.time.Instant

fun interface ModerationAdminRepository {
    fun isActiveAdmin(userId: Long): Boolean
}

fun interface ModerationReporterRepository {
    fun acquireActiveReporterLock(userId: Long): Boolean
}

interface ReviewCommentModerationRepository {
    fun findPending(cursor: CommentModerationCursor?, limit: Int): List<StoredReviewComment>
    fun findForUpdate(reviewId: Long): StoredReviewComment?
    fun saveDecision(command: ReviewCommentDecisionPersistenceCommand): StoredReviewComment
}

data class ReviewCommentDecisionPersistenceCommand(
    val reviewId: Long,
    val expectedStatus: ReviewCommentStatus,
    val nextStatus: ReviewCommentStatus,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(expectedStatus == ReviewCommentStatus.PENDING) {
            "A comment moderation decision must start from pending"
        }
        require(nextStatus in setOf(ReviewCommentStatus.PUBLISHED, ReviewCommentStatus.REJECTED)) {
            "A comment moderation decision must publish or reject"
        }
    }
}

data class StoredReviewComment(
    val reviewId: Long,
    val authorUserId: Long,
    val comment: String,
    val commentModerationStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(authorUserId > 0) { "Review author user ID must be positive" }
        require(comment.isNotBlank()) { "Moderated review comment must not be blank" }
    }
}

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
    fun existsOtherPendingByReviewId(reviewId: Long, excludedReportId: Long): Boolean
    fun findPending(cursor: ModerationCursor?, limit: Int): List<StoredReviewReport>
    fun findPendingForUpdate(reportId: Long): StoredReviewReport?
    fun findForUpdate(reportId: Long): StoredReviewReport?
    fun findOtherPendingForUpdate(reviewId: Long, excludedReportId: Long): List<StoredReviewReport> = emptyList()
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
    fun findForUpdate(reportId: Long): StoredRestaurantInfoReport?
    fun findOtherPendingForUpdate(restaurantId: Long, excludedReportId: Long): List<StoredRestaurantInfoReport> = emptyList()
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

interface ReviewReportTargetRepository {
    fun findReviewForUpdate(reviewId: Long): StoredReviewReportTarget?
    fun activeRestaurantExists(restaurantId: Long): Boolean
    fun mutate(command: ReviewReportTargetMutationCommand): StoredReviewReportTarget
}

data class StoredReviewReportTarget(
    val reviewId: Long,
    val authorUserId: Long,
    val restaurantId: Long,
    val visibilityStatus: ReviewVisibilityStatus,
    val commentStatus: ReviewCommentStatus,
    val active: Boolean,
    val submittedAt: Instant,
    val deletedAt: Instant?,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(authorUserId > 0) { "Review author user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}

data class ReviewReportTargetMutationCommand(
    val reviewId: Long,
    val expectedVisibilityStatus: ReviewVisibilityStatus,
    val nextVisibilityStatus: ReviewVisibilityStatus,
    val expectedCommentStatus: ReviewCommentStatus,
    val nextCommentStatus: ReviewCommentStatus,
    val clearCurrentPointerIfTarget: Boolean,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

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
