package com.ridervoice.api.moderation.application.model

import com.ridervoice.api.review.domain.ReviewCommentStatus
import java.time.Instant

data class CommentModerationCursor(
    val createdAt: Instant,
    val reviewId: Long,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

data class PendingReviewCommentResult(
    val reviewId: Long,
    val authorUserId: Long,
    val comment: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingReviewCommentPageResult(
    val items: List<PendingReviewCommentResult>,
    val nextCursor: CommentModerationCursor?,
)

data class ReviewCommentDecisionResult(
    val reviewId: Long,
    val commentModerationStatus: ReviewCommentStatus,
    val decidedAt: Instant,
)
