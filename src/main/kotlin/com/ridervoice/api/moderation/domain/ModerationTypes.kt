package com.ridervoice.api.moderation.domain

import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus

enum class ReportStatus {
    PENDING,
    RESOLVED,
}

enum class ReviewReportReason {
    PERSONAL_INFORMATION,
    ABUSIVE_CONTENT,
    IRRELEVANT_CONTENT,
    FALSE_INFORMATION,
    SPAM,
    OTHER,
}

enum class RestaurantInfoReportReason {
    INCORRECT_NAME,
    INCORRECT_PICKUP_LOCATION,
    DUPLICATE,
    CLOSED,
    OTHER,
}

enum class CommentModerationDecision {
    APPROVE,
    REJECT,
}

enum class ReviewReportDecision {
    DISMISS,
    HIDE_COMMENT,
    EXCLUDE_REVIEW,
}

enum class RestaurantInfoReportDecision {
    DISMISS,
    RESOLVE,
}

enum class CurrentReviewPointerAction {
    KEEP,
    CLEAR_IF_TARGET,
}

enum class ReviewCooldownAction {
    RETAIN,
}

enum class RestaurantAdminAction {
    MERGE_DUPLICATE,
    RELINK_PICKUP_LOCATION,
    RENAME,
    CLOSE,
    REOPEN,
}

enum class ModerationAuditAction {
    COMMENT_APPROVED,
    COMMENT_REJECTED,
    REVIEW_REPORT_DISMISSED,
    REVIEW_COMMENT_HIDDEN,
    REVIEW_EXCLUDED,
    RESTAURANT_REPORT_DISMISSED,
    RESTAURANT_INFO_CORRECTED,
    DUPLICATE_RESTAURANT_MERGED,
    RESTAURANT_PICKUP_RELINKED,
    RESTAURANT_RENAMED,
    RESTAURANT_CLOSED,
    RESTAURANT_REOPENED,
}

enum class ModerationTargetType {
    REVIEW,
    REVIEW_REPORT,
    RESTAURANT,
    RESTAURANT_INFO_REPORT,
}

data class CommentModerationTransition(
    val commentStatus: ReviewCommentStatus,
    val auditAction: ModerationAuditAction,
)

data class ReviewReportReceiptTransition(
    val reportStatus: ReportStatus,
    val commentStatus: ReviewCommentStatus,
    val reviewVisibilityStatus: ReviewVisibilityStatus,
)

data class ReviewReportTransition(
    val reportStatus: ReportStatus,
    val commentStatus: ReviewCommentStatus,
    val reviewVisibilityStatus: ReviewVisibilityStatus,
    val currentPointerAction: CurrentReviewPointerAction,
    val cooldownAction: ReviewCooldownAction,
    val auditAction: ModerationAuditAction,
)

data class RestaurantInfoReportTransition(
    val reportStatus: ReportStatus,
    val auditAction: ModerationAuditAction,
)
