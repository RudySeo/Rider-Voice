package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.ReportRateLimitExceededException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.ReviewReportResult
import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportPageResult
import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportResult
import com.ridervoice.api.moderation.application.model.ReportModerationCursor
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsUseCase
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ModerationCursor
import com.ridervoice.api.moderation.application.port.out.ModerationReporterRepository
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetMutationCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetRepository
import com.ridervoice.api.moderation.application.port.out.StoredRestaurantInfoReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReportTarget
import com.ridervoice.api.moderation.domain.CurrentReviewPointerAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ModerationTransitionPolicy
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
internal class ReportingService(
    private val reporters: ModerationReporterRepository,
    private val admins: ModerationAdminRepository,
    private val reviewReports: ReviewReportRepository,
    private val restaurantReports: RestaurantInfoReportRepository,
    private val targets: ReviewReportTargetRepository,
    private val audits: ModerationAuditRepository,
    private val clock: Clock,
) : CreateReviewReportUseCase,
    CreateRestaurantInfoReportUseCase,
    ListPendingReviewReportsUseCase,
    ListPendingRestaurantInfoReportsUseCase,
    DecideReviewReportUseCase,
    DecideRestaurantInfoReportUseCase {

    @Transactional
    override fun createReviewReport(command: CreateReviewReportCommand): ReviewReportResult {
        requireActiveReporterLock(command.reporterUserId)
        if (reviewReports.existsByReporterUserIdAndReviewId(command.reporterUserId, command.reviewId)) {
            throw StateConflictException("The review has already been reported by this user")
        }
        enforceReportLimit(command.reporterUserId)

        val target = targets.findReviewForUpdate(command.reviewId)
            ?: throw ResourceNotFoundException("Review report target was not found")
        if (target.visibilityStatus != ReviewVisibilityStatus.ACTIVE) {
            throw ResourceNotFoundException("Review report target was not found")
        }
        val transition = ModerationTransitionPolicy.receiveReviewReport(
            reviewVisibilityStatus = target.visibilityStatus,
            commentStatus = target.commentStatus,
        )
        if (transition.commentStatus != target.commentStatus) {
            targets.mutate(
                ReviewReportTargetMutationCommand(
                    reviewId = target.reviewId,
                    expectedVisibilityStatus = target.visibilityStatus,
                    nextVisibilityStatus = transition.reviewVisibilityStatus,
                    expectedCommentStatus = target.commentStatus,
                    nextCommentStatus = transition.commentStatus,
                    clearCurrentPointerIfTarget = false,
                ),
            )
        }

        return reviewReports.create(
            NewReviewReportPersistenceCommand(
                reporterUserId = command.reporterUserId,
                reviewId = command.reviewId,
                reason = command.reason,
                details = command.details,
            ),
        ).toResult()
    }

    @Transactional
    override fun createRestaurantInfoReport(
        command: CreateRestaurantInfoReportCommand,
    ): RestaurantInfoReportResult {
        requireActiveReporterLock(command.reporterUserId)
        if (
            restaurantReports.existsByReporterUserIdAndRestaurantId(
                command.reporterUserId,
                command.restaurantId,
            )
        ) {
            throw StateConflictException("The restaurant has already been reported by this user")
        }
        enforceReportLimit(command.reporterUserId)
        if (!targets.activeRestaurantExists(command.restaurantId)) {
            throw ResourceNotFoundException("Restaurant report target was not found")
        }

        return restaurantReports.create(
            NewRestaurantInfoReportPersistenceCommand(
                reporterUserId = command.reporterUserId,
                restaurantId = command.restaurantId,
                reason = command.reason,
                details = command.details,
            ),
        ).toResult()
    }

    @Transactional(readOnly = true)
    override fun list(query: ListPendingReviewReportsQuery): PendingReviewReportPageResult {
        requireActiveAdmin(query.adminUserId)
        val page = reviewReports.findPending(query.cursor?.toPersistenceCursor(), query.size + 1)
        val visibleItems = page.take(query.size)
        return PendingReviewReportPageResult(
            items = visibleItems.map {
                PendingReviewReportResult(
                    reportId = it.reportId,
                    reporterUserId = it.reporterUserId,
                    reviewId = it.reviewId,
                    reason = it.reason,
                    details = it.details,
                    createdAt = it.createdAt,
                )
            },
            nextCursor = visibleItems.lastOrNull()
                ?.takeIf { page.size > query.size }
                ?.let { ReportModerationCursor(it.createdAt, it.reportId) },
        )
    }

    @Transactional(readOnly = true)
    override fun list(query: ListPendingRestaurantInfoReportsQuery): PendingRestaurantInfoReportPageResult {
        requireActiveAdmin(query.adminUserId)
        val page = restaurantReports.findPending(query.cursor?.toPersistenceCursor(), query.size + 1)
        val visibleItems = page.take(query.size)
        return PendingRestaurantInfoReportPageResult(
            items = visibleItems.map {
                PendingRestaurantInfoReportResult(
                    reportId = it.reportId,
                    reporterUserId = it.reporterUserId,
                    restaurantId = it.restaurantId,
                    reason = it.reason,
                    details = it.details,
                    createdAt = it.createdAt,
                )
            },
            nextCursor = visibleItems.lastOrNull()
                ?.takeIf { page.size > query.size }
                ?.let { ReportModerationCursor(it.createdAt, it.reportId) },
        )
    }

    @Transactional
    override fun decideReviewReport(command: DecideReviewReportCommand): ReviewReportResult {
        requireActiveAdmin(command.adminUserId)
        val report = reviewReports.findForUpdate(command.reportId)
            ?: throw ResourceNotFoundException("Review report was not found")
        if (report.status != ReportStatus.PENDING) {
            throw StateConflictException("Review report has already been resolved")
        }
        val target = targets.findReviewForUpdate(report.reviewId)
            ?: throw ResourceNotFoundException("Reported review was not found")
        if (target.visibilityStatus != ReviewVisibilityStatus.ACTIVE) {
            throw StateConflictException("Reported review is no longer active")
        }

        val transition = try {
            ModerationTransitionPolicy.decideReviewReport(
                reportStatus = report.status,
                reviewVisibilityStatus = target.visibilityStatus,
                commentStatus = target.commentStatus,
                decision = command.decision,
            )
        } catch (exception: IllegalStateException) {
            throw StateConflictException("Review report cannot receive this decision", exception)
        }
        val keepHiddenForOtherReports =
            command.decision == ReviewReportDecision.DISMISS &&
                target.commentStatus == ReviewCommentStatus.HIDDEN_REPORTED &&
                reviewReports.existsOtherPendingByReviewId(report.reviewId, report.reportId)
        val nextCommentStatus = if (keepHiddenForOtherReports) {
            ReviewCommentStatus.HIDDEN_REPORTED
        } else {
            transition.commentStatus
        }
        val savedTarget = targets.mutate(
            ReviewReportTargetMutationCommand(
                reviewId = target.reviewId,
                expectedVisibilityStatus = target.visibilityStatus,
                nextVisibilityStatus = transition.reviewVisibilityStatus,
                expectedCommentStatus = target.commentStatus,
                nextCommentStatus = nextCommentStatus,
                clearCurrentPointerIfTarget =
                    transition.currentPointerAction == CurrentReviewPointerAction.CLEAR_IF_TARGET,
            ),
        )
        val decidedAt = clock.instant()
        val savedReport = reviewReports.saveDecision(
            ReviewReportDecisionPersistenceCommand(
                reportId = report.reportId,
                decision = command.decision,
                decidedByUserId = command.adminUserId,
                decidedAt = decidedAt,
            ),
        )
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = command.adminUserId,
                action = transition.auditAction,
                targetType = ModerationTargetType.REVIEW_REPORT,
                targetId = report.reportId,
                reason = command.reason,
                beforeState = reviewReportState(report.status, target),
                afterState = reviewReportState(savedReport.status, savedTarget),
                occurredAt = decidedAt,
            ),
        )
        return savedReport.toResult()
    }

    @Transactional
    override fun decideRestaurantInfoReport(
        command: DecideRestaurantInfoReportCommand,
    ): RestaurantInfoReportResult {
        requireActiveAdmin(command.adminUserId)
        val report = restaurantReports.findForUpdate(command.reportId)
            ?: throw ResourceNotFoundException("Restaurant information report was not found")
        if (report.status != ReportStatus.PENDING) {
            throw StateConflictException("Restaurant information report has already been resolved")
        }
        val transition = ModerationTransitionPolicy.decideRestaurantInfoReport(
            reportStatus = report.status,
            decision = command.decision,
        )
        val decidedAt = clock.instant()
        val saved = restaurantReports.saveDecision(
            RestaurantInfoReportDecisionPersistenceCommand(
                reportId = report.reportId,
                decision = command.decision,
                decidedByUserId = command.adminUserId,
                decidedAt = decidedAt,
            ),
        )
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = command.adminUserId,
                action = transition.auditAction,
                targetType = ModerationTargetType.RESTAURANT_INFO_REPORT,
                targetId = report.reportId,
                reason = command.reason,
                beforeState = restaurantReportState(report),
                afterState = restaurantReportState(saved),
                occurredAt = decidedAt,
            ),
        )
        return saved.toResult()
    }

    private fun enforceReportLimit(reporterUserId: Long) {
        val since = clock.instant().minus(REPORT_WINDOW)
        val recentCount = reviewReports.countByReporterUserIdSince(reporterUserId, since) +
            restaurantReports.countByReporterUserIdSince(reporterUserId, since)
        if (recentCount >= REPORT_LIMIT) {
            throw ReportRateLimitExceededException()
        }
    }

    private fun requireActiveReporterLock(userId: Long) {
        if (!reporters.acquireActiveReporterLock(userId)) {
            throw ApiException(ApiErrorCode.ACCESS_DENIED, "Active user status is required")
        }
    }

    private fun requireActiveAdmin(userId: Long) {
        if (!admins.isActiveAdmin(userId)) {
            throw ApiException(ApiErrorCode.ACCESS_DENIED, "Active administrator role is required")
        }
    }

    private fun StoredReviewReport.toResult() = ReviewReportResult(
        reportId = reportId,
        reviewId = reviewId,
        reason = reason,
        status = status,
        decision = decision,
        createdAt = createdAt,
        decidedAt = decidedAt,
    )

    private fun StoredRestaurantInfoReport.toResult() = RestaurantInfoReportResult(
        reportId = reportId,
        restaurantId = restaurantId,
        reason = reason,
        status = status,
        decision = decision,
        createdAt = createdAt,
        decidedAt = decidedAt,
    )

    private fun ReportModerationCursor.toPersistenceCursor() = ModerationCursor(createdAt, reportId)

    private fun reviewReportState(status: ReportStatus, target: StoredReviewReportTarget): String =
        "{\"reportStatus\":\"${status.name}\"," +
            "\"reviewVisibilityStatus\":\"${target.visibilityStatus.name}\"," +
            "\"commentModerationStatus\":\"${target.commentStatus.name}\"," +
            "\"currentReviewId\":${target.currentReviewId ?: "null"}," +
            "\"lastSubmittedAt\":\"${target.lastSubmittedAt}\"," +
            "\"lastSequence\":${target.lastSequence}}"

    private fun restaurantReportState(report: StoredRestaurantInfoReport): String =
        "{\"reportStatus\":\"${report.status.name}\"," +
            "\"decision\":${report.decision?.let { "\"${it.name}\"" } ?: "null"}}"

    private companion object {
        const val REPORT_LIMIT = 20L
        val REPORT_WINDOW: Duration = Duration.ofHours(24)
    }
}
