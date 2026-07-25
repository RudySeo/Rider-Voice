package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.moderation.application.model.PendingReviewCommentPageResult
import com.ridervoice.api.moderation.application.model.ReviewCommentDecisionResult
import com.ridervoice.api.moderation.domain.CommentModerationDecision

data class ListPendingReviewCommentsQuery(
    val adminUserId: Long,
    val cursor: CommentModerationCursor?,
    val size: Int,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(size in 1..50) { "Comment moderation page size must be between 1 and 50" }
    }
}

fun interface ListPendingReviewCommentsUseCase {
    fun list(query: ListPendingReviewCommentsQuery): PendingReviewCommentPageResult
}

data class DecideReviewCommentCommand(
    val adminUserId: Long,
    val reviewId: Long,
    val decision: CommentModerationDecision,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

fun interface DecideReviewCommentUseCase {
    fun decide(command: DecideReviewCommentCommand): ReviewCommentDecisionResult
}
