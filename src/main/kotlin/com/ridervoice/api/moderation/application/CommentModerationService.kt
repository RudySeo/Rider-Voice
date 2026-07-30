package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.moderation.application.model.PendingReviewCommentPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewCommentResult
import com.ridervoice.api.moderation.application.model.ReviewCommentDecisionResult
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewCommentCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewCommentUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewCommentsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewCommentsUseCase
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ReviewCommentDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewCommentModerationRepository
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ModerationTransitionPolicy
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
internal class CommentModerationService(
    private val admins: ModerationAdminRepository,
    private val comments: ReviewCommentModerationRepository,
    private val audits: ModerationAuditRepository,
    private val clock: Clock,
) : ListPendingReviewCommentsUseCase, DecideReviewCommentUseCase {

    @Transactional(readOnly = true)
    override fun list(query: ListPendingReviewCommentsQuery): PendingReviewCommentPageResult {
        requireActiveAdmin(query.adminUserId)
        val page = comments.findPending(query.cursor, query.size + 1)
        val visibleItems = page.take(query.size)
        return PendingReviewCommentPageResult(
            items = visibleItems.map {
                PendingReviewCommentResult(
                    reviewId = it.reviewId,
                    authorUserId = it.authorUserId,
                    comment = it.comment,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            nextCursor = if (page.size > query.size) {
                visibleItems.last().let { CommentModerationCursor(it.createdAt, it.reviewId) }
            } else {
                null
            },
        )
    }

    @Transactional
    override fun decide(command: DecideReviewCommentCommand): ReviewCommentDecisionResult {
        requireActiveAdmin(command.adminUserId)
        val target = comments.findForUpdate(command.reviewId)
            ?: throw ResourceNotFoundException("Review comment moderation target was not found")
        if (
            target.commentModerationStatus != ReviewCommentStatus.PENDING ||
            target.visibilityStatus != ReviewVisibilityStatus.ACTIVE
        ) {
            throw StateConflictException("Review comment has already been moderated")
        }

        val transition = ModerationTransitionPolicy.decideComment(
            currentStatus = target.commentModerationStatus,
            decision = command.decision,
        )
        val decidedAt = clock.instant()
        val saved = comments.saveDecision(
            ReviewCommentDecisionPersistenceCommand(
                reviewId = target.reviewId,
                expectedStatus = target.commentModerationStatus,
                nextStatus = transition.commentStatus,
            ),
        )
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = command.adminUserId,
                action = transition.auditAction,
                targetType = ModerationTargetType.REVIEW,
                targetId = target.reviewId,
                reason = null,
                beforeState = commentState(target.commentModerationStatus),
                afterState = commentState(saved.commentModerationStatus),
                occurredAt = decidedAt,
            ),
        )
        return ReviewCommentDecisionResult(
            reviewId = saved.reviewId,
            commentModerationStatus = saved.commentModerationStatus,
            decidedAt = decidedAt,
        )
    }

    private fun requireActiveAdmin(userId: Long) {
        if (!admins.isActiveAdmin(userId)) {
            throw ApiException(ApiErrorCode.ACCESS_DENIED, "Active administrator role is required")
        }
    }

    private fun commentState(status: ReviewCommentStatus): String =
        "{\"commentModerationStatus\":\"${status.name}\"}"
}
