package com.ridervoice.api.moderation.domain

import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus

object ModerationTransitionPolicy {

    fun decideComment(
        currentStatus: ReviewCommentStatus,
        decision: CommentModerationDecision,
    ): CommentModerationTransition {
        check(currentStatus == ReviewCommentStatus.PENDING) {
            "Only a pending comment can receive a moderation decision"
        }

        return when (decision) {
            CommentModerationDecision.APPROVE -> CommentModerationTransition(
                commentStatus = ReviewCommentStatus.PUBLISHED,
                auditAction = ModerationAuditAction.COMMENT_APPROVED,
            )

            CommentModerationDecision.REJECT -> CommentModerationTransition(
                commentStatus = ReviewCommentStatus.REJECTED,
                auditAction = ModerationAuditAction.COMMENT_REJECTED,
            )
        }
    }

    fun receiveReviewReport(
        reviewVisibilityStatus: ReviewVisibilityStatus,
        commentStatus: ReviewCommentStatus,
    ): ReviewReportReceiptTransition {
        check(reviewVisibilityStatus == ReviewVisibilityStatus.ACTIVE) {
            "Only an active review can be reported"
        }

        val nextCommentStatus = if (commentStatus == ReviewCommentStatus.PUBLISHED) {
            ReviewCommentStatus.HIDDEN_REPORTED
        } else {
            commentStatus
        }
        return ReviewReportReceiptTransition(
            reportStatus = ReportStatus.PENDING,
            commentStatus = nextCommentStatus,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
        )
    }

    fun decideReviewReport(
        reportStatus: ReportStatus,
        reviewVisibilityStatus: ReviewVisibilityStatus,
        commentStatus: ReviewCommentStatus,
        decision: ReviewReportDecision,
    ): ReviewReportTransition {
        check(reportStatus == ReportStatus.PENDING) {
            "Only a pending review report can receive a decision"
        }
        check(reviewVisibilityStatus == ReviewVisibilityStatus.ACTIVE) {
            "Only an active review can receive a report decision"
        }

        return when (decision) {
            ReviewReportDecision.DISMISS -> ReviewReportTransition(
                reportStatus = ReportStatus.RESOLVED,
                commentStatus = restoreReportedComment(commentStatus),
                reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                currentPointerAction = CurrentReviewPointerAction.KEEP,
                cooldownAction = ReviewCooldownAction.RETAIN,
                auditAction = ModerationAuditAction.REVIEW_REPORT_DISMISSED,
            )

            ReviewReportDecision.HIDE_COMMENT -> {
                check(commentStatus == ReviewCommentStatus.HIDDEN_REPORTED) {
                    "Only a comment hidden by a report can be permanently hidden"
                }
                ReviewReportTransition(
                    reportStatus = ReportStatus.RESOLVED,
                    commentStatus = ReviewCommentStatus.REJECTED,
                    reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                    currentPointerAction = CurrentReviewPointerAction.KEEP,
                    cooldownAction = ReviewCooldownAction.RETAIN,
                    auditAction = ModerationAuditAction.REVIEW_COMMENT_HIDDEN,
                )
            }

            ReviewReportDecision.EXCLUDE_REVIEW -> ReviewReportTransition(
                reportStatus = ReportStatus.RESOLVED,
                commentStatus = commentStatus,
                reviewVisibilityStatus = ReviewVisibilityStatus.EXCLUDED,
                currentPointerAction = CurrentReviewPointerAction.CLEAR_IF_TARGET,
                cooldownAction = ReviewCooldownAction.RETAIN,
                auditAction = ModerationAuditAction.REVIEW_EXCLUDED,
            )
        }
    }

    fun decideRestaurantInfoReport(
        reportStatus: ReportStatus,
        decision: RestaurantInfoReportDecision,
    ): RestaurantInfoReportTransition {
        check(reportStatus == ReportStatus.PENDING) {
            "Only a pending restaurant information report can receive a decision"
        }

        val auditAction = when (decision) {
            RestaurantInfoReportDecision.DISMISS -> ModerationAuditAction.RESTAURANT_REPORT_DISMISSED
            RestaurantInfoReportDecision.RESOLVE -> ModerationAuditAction.RESTAURANT_INFO_CORRECTED
        }
        return RestaurantInfoReportTransition(
            reportStatus = ReportStatus.RESOLVED,
            auditAction = auditAction,
        )
    }

    private fun restoreReportedComment(commentStatus: ReviewCommentStatus): ReviewCommentStatus =
        if (commentStatus == ReviewCommentStatus.HIDDEN_REPORTED) {
            ReviewCommentStatus.PUBLISHED
        } else {
            commentStatus
        }
}

object ModerationAuditPolicy {
    fun actionFor(action: RestaurantAdminAction): ModerationAuditAction = when (action) {
        RestaurantAdminAction.MERGE_DUPLICATE -> ModerationAuditAction.DUPLICATE_RESTAURANT_MERGED
        RestaurantAdminAction.RELINK_PICKUP_LOCATION -> ModerationAuditAction.RESTAURANT_PICKUP_RELINKED
    }
}
