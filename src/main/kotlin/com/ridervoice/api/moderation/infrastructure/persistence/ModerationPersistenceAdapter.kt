package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ModerationCursor
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportRepository
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.application.port.out.StoredRestaurantInfoReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReport
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.ReviewReport
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.domain.Review
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

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
