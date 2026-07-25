package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationReporterRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ReviewCommentDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewCommentModerationRepository
import com.ridervoice.api.moderation.application.port.out.ModerationCursor
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetMutationCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetRepository
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.application.port.out.StoredReviewComment
import com.ridervoice.api.moderation.application.port.out.StoredRestaurantInfoReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReportTarget
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.ReviewReport
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Component
internal class ModerationAdminPersistenceAdapter(
    private val users: SpringDataModerationAdminRepository,
) : ModerationAdminRepository {
    override fun isActiveAdmin(userId: Long): Boolean = users.existsByIdAndRoleAndStatus(
        userId,
        UserRole.ADMIN,
        UserStatus.ACTIVE,
    )
}

@Component
internal class ModerationReporterPersistenceAdapter(
    private val users: SpringDataModerationReporterRepository,
) : ModerationReporterRepository {
    override fun acquireActiveReporterLock(userId: Long): Boolean =
        users.findActiveForUpdate(userId, UserStatus.ACTIVE).isPresent
}

@Component
internal class ReviewCommentModerationPersistenceAdapter(
    private val reviews: SpringDataReviewCommentModerationRepository,
) : ReviewCommentModerationRepository {

    override fun findPending(
        cursor: CommentModerationCursor?,
        limit: Int,
    ): List<StoredReviewComment> {
        require(limit > 0) { "Comment moderation query limit must be positive" }
        val pageable = PageRequest.of(0, limit)
        val rows = if (cursor == null) {
            reviews.findAllPendingComments(
                ReviewCommentStatus.PENDING,
                ReviewVisibilityStatus.ACTIVE,
                pageable,
            )
        } else {
            reviews.findAllPendingCommentsBeforeCursor(
                ReviewCommentStatus.PENDING,
                ReviewVisibilityStatus.ACTIVE,
                cursor.createdAt,
                cursor.reviewId,
                pageable,
            )
        }
        return rows.map { it.toStoredComment() }
    }

    override fun findForUpdate(reviewId: Long): StoredReviewComment? =
        reviews.findByIdForUpdate(reviewId).orElse(null)?.toStoredComment()

    override fun saveDecision(
        command: ReviewCommentDecisionPersistenceCommand,
    ): StoredReviewComment {
        val review = reviews.findByIdForUpdate(command.reviewId).orElseThrow {
            IllegalStateException("Review ${command.reviewId} disappeared during comment moderation")
        }
        check(review.commentModerationStatus == command.expectedStatus) {
            "Review ${command.reviewId} comment status changed during moderation"
        }
        when (command.nextStatus) {
            ReviewCommentStatus.PUBLISHED -> review.publishComment()
            ReviewCommentStatus.REJECTED -> review.rejectComment()
            else -> error("Unsupported comment moderation target status: ${command.nextStatus}")
        }
        return reviews.saveAndFlush(review).toStoredComment()
    }

    private fun Review.toStoredComment() = StoredReviewComment(
        reviewId = id,
        authorUserId = author.id,
        comment = requireNotNull(comment) { "Pending review comment must exist" },
        commentModerationStatus = commentModerationStatus,
        visibilityStatus = visibilityStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Component
internal class ReviewReportPersistenceAdapter(
    private val reports: SpringDataReviewReportRepository,
    private val entityManager: EntityManager,
) : ReviewReportRepository {

    override fun create(command: NewReviewReportPersistenceCommand): StoredReviewReport = reports.saveAndFlush(
        ReviewReport(
            reporter = entityManager.getReference(User::class.java, command.reporterUserId),
            review = entityManager.getReference(Review::class.java, command.reviewId),
            reason = command.reason,
            details = command.details,
        ),
    ).toSnapshot()

    override fun existsByReporterUserIdAndReviewId(reporterUserId: Long, reviewId: Long): Boolean =
        reports.existsByReporterIdAndReviewId(reporterUserId, reviewId)

    override fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long =
        reports.countByReporterIdAndCreatedAtGreaterThanEqual(reporterUserId, since)

    override fun existsOtherPendingByReviewId(reviewId: Long, excludedReportId: Long): Boolean =
        reports.countByReviewIdAndStatusAndIdNot(reviewId, ReportStatus.PENDING, excludedReportId) > 0

    override fun findPending(cursor: ModerationCursor?, limit: Int): List<StoredReviewReport> {
        require(limit > 0) { "Review report query limit must be positive" }
        val pageable = PageRequest.of(0, limit)
        val rows = if (cursor == null) {
            reports.findAllPending(ReportStatus.PENDING, pageable)
        } else {
            reports.findAllPendingBeforeCursor(
                ReportStatus.PENDING,
                cursor.createdAt,
                cursor.id,
                pageable,
            )
        }
        return rows.map { it.toSnapshot() }
    }

    override fun findPendingForUpdate(reportId: Long): StoredReviewReport? =
        reports.findPendingForUpdate(reportId, ReportStatus.PENDING).orElse(null)?.toSnapshot()

    override fun findForUpdate(reportId: Long): StoredReviewReport? =
        reports.findByIdForUpdate(reportId).orElse(null)?.toSnapshot()

    override fun saveDecision(command: ReviewReportDecisionPersistenceCommand): StoredReviewReport {
        val report = reports.findPendingForUpdate(command.reportId, ReportStatus.PENDING).orElseThrow {
            IllegalStateException("Review report ${command.reportId} is not pending")
        }
        report.resolve(
            command.decision,
            entityManager.getReference(User::class.java, command.decidedByUserId),
            command.decidedAt,
        )
        return reports.saveAndFlush(report).toSnapshot()
    }

    private fun ReviewReport.toSnapshot() = StoredReviewReport(
        reportId = id,
        reporterUserId = reporter.id,
        reviewId = review.id,
        reason = reason,
        details = details,
        status = status,
        decision = decision,
        decidedByUserId = decidedBy?.id,
        decidedAt = decidedAt,
        createdAt = createdAt,
    )
}

@Component
internal class RestaurantInfoReportPersistenceAdapter(
    private val reports: SpringDataRestaurantInfoReportRepository,
    private val entityManager: EntityManager,
) : RestaurantInfoReportRepository {

    override fun create(
        command: NewRestaurantInfoReportPersistenceCommand,
    ): StoredRestaurantInfoReport = reports.saveAndFlush(
        RestaurantInfoReport(
            reporter = entityManager.getReference(User::class.java, command.reporterUserId),
            restaurant = entityManager.getReference(Restaurant::class.java, command.restaurantId),
            reason = command.reason,
            details = command.details,
        ),
    ).toSnapshot()

    override fun existsByReporterUserIdAndRestaurantId(
        reporterUserId: Long,
        restaurantId: Long,
    ): Boolean = reports.existsByReporterIdAndRestaurantId(reporterUserId, restaurantId)

    override fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long =
        reports.countByReporterIdAndCreatedAtGreaterThanEqual(reporterUserId, since)

    override fun findPending(cursor: ModerationCursor?, limit: Int): List<StoredRestaurantInfoReport> {
        require(limit > 0) { "Restaurant report query limit must be positive" }
        val pageable = PageRequest.of(0, limit)
        val rows = if (cursor == null) {
            reports.findAllPending(ReportStatus.PENDING, pageable)
        } else {
            reports.findAllPendingBeforeCursor(
                ReportStatus.PENDING,
                cursor.createdAt,
                cursor.id,
                pageable,
            )
        }
        return rows.map { it.toSnapshot() }
    }

    override fun findPendingForUpdate(reportId: Long): StoredRestaurantInfoReport? =
        reports.findPendingForUpdate(reportId, ReportStatus.PENDING).orElse(null)?.toSnapshot()

    override fun findForUpdate(reportId: Long): StoredRestaurantInfoReport? =
        reports.findByIdForUpdate(reportId).orElse(null)?.toSnapshot()

    override fun saveDecision(
        command: RestaurantInfoReportDecisionPersistenceCommand,
    ): StoredRestaurantInfoReport {
        val report = reports.findPendingForUpdate(command.reportId, ReportStatus.PENDING).orElseThrow {
            IllegalStateException("Restaurant information report ${command.reportId} is not pending")
        }
        report.resolve(
            command.decision,
            entityManager.getReference(User::class.java, command.decidedByUserId),
            command.decidedAt,
        )
        return reports.saveAndFlush(report).toSnapshot()
    }

    private fun RestaurantInfoReport.toSnapshot() = StoredRestaurantInfoReport(
        reportId = id,
        reporterUserId = reporter.id,
        restaurantId = restaurant.id,
        reason = reason,
        details = details,
        status = status,
        decision = decision,
        decidedByUserId = decidedBy?.id,
        decidedAt = decidedAt,
        createdAt = createdAt,
    )
}

@Component
internal class ReviewReportTargetPersistenceAdapter(
    private val reviews: SpringDataModerationReviewTargetRepository,
    private val states: SpringDataModerationReviewStateRepository,
    private val restaurants: SpringDataModerationRestaurantTargetRepository,
) : ReviewReportTargetRepository {

    override fun findReviewForUpdate(reviewId: Long): StoredReviewReportTarget? {
        val review = reviews.findByIdForUpdate(reviewId).orElse(null) ?: return null
        val state = findStateForUpdate(review)
        return snapshot(review, state)
    }

    override fun activeRestaurantExists(restaurantId: Long): Boolean =
        restaurants.existsByIdAndStatus(restaurantId, RestaurantStatus.ACTIVE)

    override fun mutate(command: ReviewReportTargetMutationCommand): StoredReviewReportTarget {
        val review = reviews.findByIdForUpdate(command.reviewId).orElseThrow {
            IllegalStateException("Review ${command.reviewId} disappeared during report handling")
        }
        val state = findStateForUpdate(review)
        check(review.visibilityStatus == command.expectedVisibilityStatus) {
            "Review ${command.reviewId} visibility changed during report handling"
        }
        check(review.commentModerationStatus == command.expectedCommentStatus) {
            "Review ${command.reviewId} comment status changed during report handling"
        }

        mutateComment(review, command.nextCommentStatus)
        if (review.visibilityStatus != command.nextVisibilityStatus) {
            check(command.nextVisibilityStatus == ReviewVisibilityStatus.EXCLUDED) {
                "Unsupported review visibility transition"
            }
            review.exclude()
        }
        if (command.clearCurrentPointerIfTarget && state.clearCurrentReviewIf(review.id)) {
            states.saveAndFlush(state)
        }
        return snapshot(reviews.saveAndFlush(review), state)
    }

    private fun findStateForUpdate(review: Review): AuthorRestaurantReviewState =
        states.findForUpdate(review.author.id, review.restaurant.id).orElseThrow {
            IllegalStateException("Review ${review.id} has no author-restaurant cooldown state")
        }

    private fun mutateComment(review: Review, nextStatus: ReviewCommentStatus) {
        if (review.commentModerationStatus == nextStatus) return
        when (review.commentModerationStatus to nextStatus) {
            ReviewCommentStatus.PUBLISHED to ReviewCommentStatus.HIDDEN_REPORTED ->
                review.hidePublishedCommentForReport()

            ReviewCommentStatus.HIDDEN_REPORTED to ReviewCommentStatus.PUBLISHED ->
                review.restoreReportedComment()

            ReviewCommentStatus.HIDDEN_REPORTED to ReviewCommentStatus.REJECTED ->
                review.permanentlyHideReportedComment()

            else -> error(
                "Unsupported reported comment transition: " +
                    "${review.commentModerationStatus} -> $nextStatus",
            )
        }
    }

    private fun snapshot(
        review: Review,
        state: AuthorRestaurantReviewState,
    ) = StoredReviewReportTarget(
        reviewId = review.id,
        authorUserId = review.author.id,
        restaurantId = review.restaurant.id,
        visibilityStatus = review.visibilityStatus,
        commentStatus = review.commentModerationStatus,
        currentReviewId = state.currentReview?.id,
        lastSubmittedAt = state.lastSubmittedAt,
        lastSequence = state.lastSequence,
    )
}

@Component
internal class ModerationAuditPersistenceAdapter(
    private val audits: SpringDataModerationAuditRepository,
    private val entityManager: EntityManager,
) : ModerationAuditRepository {

    override fun append(command: ModerationAuditPersistenceCommand): StoredModerationAudit =
        audits.saveAndFlush(
            ModerationAudit(
                actor = entityManager.getReference(User::class.java, command.actorUserId),
                action = command.action,
                targetType = command.targetType,
                targetId = command.targetId,
                reason = command.reason,
                beforeState = command.beforeState,
                afterState = command.afterState,
                occurredAt = command.occurredAt,
            ),
        ).toSnapshot()

    private fun ModerationAudit.toSnapshot() = StoredModerationAudit(
        auditId = id,
        actorUserId = actor.id,
        action = action,
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        beforeState = beforeState,
        afterState = afterState,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )
}
