package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsQuery
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ModerationReporterRepository
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetMutationCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetRepository
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.application.port.out.StoredRestaurantInfoReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReport
import com.ridervoice.api.moderation.application.port.out.StoredReviewReportTarget
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReportingServiceTest {

    @Test
    fun `review report hides only a published comment and keeps structured ratings active`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.PUBLISHED))

        val result = fixture.service.createReviewReport(
            CreateReviewReportCommand(REPORTER_ID, REVIEW_ID, ReviewReportReason.SPAM, "반복 의견"),
        )

        assertThat(result.status).isEqualTo(ReportStatus.PENDING)
        assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertThat(fixture.targets.current.commentStatus).isEqualTo(ReviewCommentStatus.HIDDEN_REPORTED)
        assertThat(fixture.reviewReports.created.single().details).isEqualTo("반복 의견")
        assertThat(fixture.audits.commands).isEmpty()
    }

    @Test
    fun `review report leaves non-published comments unchanged`() {
        ReviewCommentStatus.entries.filterNot { it == ReviewCommentStatus.PUBLISHED }.forEach { status ->
            val fixture = fixture(reviewTarget(commentStatus = status))

            fixture.service.createReviewReport(
                CreateReviewReportCommand(REPORTER_ID, REVIEW_ID, ReviewReportReason.OTHER, null),
            )

            assertThat(fixture.targets.current.commentStatus).isEqualTo(status)
            assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        }
    }

    @Test
    fun `duplicate target report is rejected before review mutation`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.PUBLISHED)).also {
            it.reviewReports.duplicate = true
        }

        assertThatThrownBy {
            fixture.service.createReviewReport(
                CreateReviewReportCommand(REPORTER_ID, REVIEW_ID, ReviewReportReason.SPAM, null),
            )
        }.isInstanceOf(StateConflictException::class.java)

        assertThat(fixture.targets.current.commentStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
        assertThat(fixture.reviewReports.created).isEmpty()
    }

    @Test
    fun `review and restaurant reports share the rolling 24 hour limit of twenty`() {
        val fixture = fixture(reviewTarget()).also {
            it.reviewReports.recentCount = 12
            it.restaurantReports.recentCount = 8
        }

        assertThatThrownBy {
            fixture.service.createRestaurantInfoReport(
                CreateRestaurantInfoReportCommand(
                    REPORTER_ID,
                    RESTAURANT_ID,
                    RestaurantInfoReportReason.INCORRECT_NAME,
                    null,
                ),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.errorCode).isEqualTo(ApiErrorCode.REPORT_RATE_LIMIT_EXCEEDED)
        }

        assertThat(fixture.reviewReports.lastCountSince).isEqualTo(NOW.minusSeconds(86_400))
        assertThat(fixture.restaurantReports.lastCountSince).isEqualTo(NOW.minusSeconds(86_400))
        assertThat(fixture.restaurantReports.created).isEmpty()
    }

    @Test
    fun `active user can create a restaurant information report`() {
        val fixture = fixture(reviewTarget())

        val result = fixture.service.createRestaurantInfoReport(
            CreateRestaurantInfoReportCommand(
                REPORTER_ID,
                RESTAURANT_ID,
                RestaurantInfoReportReason.DUPLICATE,
                "같은 음식점으로 보임",
            ),
        )

        assertThat(result.restaurantId).isEqualTo(RESTAURANT_ID)
        assertThat(result.status).isEqualTo(ReportStatus.PENDING)
        assertThat(fixture.restaurantReports.created.single().reason)
            .isEqualTo(RestaurantInfoReportReason.DUPLICATE)
    }

    @Test
    fun `active admin can list pending review and restaurant report queues`() {
        val fixture = fixture(reviewTarget()).also {
            it.reviewReports.seedPending()
            it.restaurantReports.seedPending()
        }

        val reviewPage = fixture.service.list(ListPendingReviewReportsQuery(ADMIN_ID, null, 20))
        val restaurantPage = fixture.service.list(ListPendingRestaurantInfoReportsQuery(ADMIN_ID, null, 20))

        assertThat(reviewPage.items.single().reportId).isEqualTo(REVIEW_REPORT_ID)
        assertThat(reviewPage.items.single().reporterUserId).isEqualTo(REPORTER_ID)
        assertThat(reviewPage.nextCursor).isNull()
        assertThat(restaurantPage.items.single().reportId).isEqualTo(RESTAURANT_REPORT_ID)
        assertThat(restaurantPage.items.single().restaurantId).isEqualTo(RESTAURANT_ID)
        assertThat(restaurantPage.nextCursor).isNull()
    }

    @Test
    fun `dismiss restores a temporarily hidden published comment and audits the decision`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.HIDDEN_REPORTED)).also {
            it.reviewReports.seedPending()
        }

        val result = fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.DISMISS, "위반 아님"),
        )

        assertThat(result.decision).isEqualTo(ReviewReportDecision.DISMISS)
        assertThat(fixture.targets.current.commentStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
        assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertAudit(fixture.audits.commands.single(), ModerationAuditAction.REVIEW_REPORT_DISMISSED)
    }

    @Test
    fun `dismiss keeps comment hidden while another report is pending`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.HIDDEN_REPORTED)).also {
            it.reviewReports.seedPending()
            it.reviewReports.otherPendingForReview = true
        }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.DISMISS, null),
        )

        assertThat(fixture.targets.current.commentStatus).isEqualTo(ReviewCommentStatus.HIDDEN_REPORTED)
    }

    @Test
    fun `hide comment rejects only the reported comment and preserves structured visibility`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.HIDDEN_REPORTED)).also {
            it.reviewReports.seedPending()
        }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.HIDE_COMMENT, null),
        )

        assertThat(fixture.targets.current.commentStatus).isEqualTo(ReviewCommentStatus.REJECTED)
        assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertAudit(fixture.audits.commands.single(), ModerationAuditAction.REVIEW_COMMENT_HIDDEN)
    }

    @Test
    fun `exclude clears only matching current pointer removes aggregate input and retains cooldown`() {
        val fixture = fixture(
            reviewTarget(
                commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                currentReviewId = REVIEW_ID,
                lastSubmittedAt = LAST_SUBMITTED_AT,
                lastSequence = 3,
            ),
        ).also { it.reviewReports.seedPending() }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.EXCLUDE_REVIEW, "도배"),
        )

        assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.EXCLUDED)
        assertThat(fixture.targets.current.currentReviewId).isNull()
        assertThat(fixture.targets.current.lastSubmittedAt).isEqualTo(LAST_SUBMITTED_AT)
        assertThat(fixture.targets.current.lastSequence).isEqualTo(3)
        assertThat(fixture.targets.currentAggregateReviewIds()).isEmpty()
        assertThat(fixture.targets.historyReviewIds).containsExactly(REVIEW_ID - 1)
        assertAudit(fixture.audits.commands.single(), ModerationAuditAction.REVIEW_EXCLUDED)
    }

    @Test
    fun `excluding a history review does not clear a newer current review`() {
        val fixture = fixture(reviewTarget(currentReviewId = REVIEW_ID + 1)).also {
            it.reviewReports.seedPending()
        }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.EXCLUDE_REVIEW, null),
        )

        assertThat(fixture.targets.current.currentReviewId).isEqualTo(REVIEW_ID + 1)
    }

    @Test
    fun `restaurant information report decisions resolve once and append matching audits`() {
        RestaurantInfoReportDecision.entries.forEach { decision ->
            val fixture = fixture(reviewTarget()).also { it.restaurantReports.seedPending() }

            val result = fixture.service.decideRestaurantInfoReport(
                DecideRestaurantInfoReportCommand(ADMIN_ID, RESTAURANT_REPORT_ID, decision, "확인 완료"),
            )

            val expectedAction = when (decision) {
                RestaurantInfoReportDecision.DISMISS -> ModerationAuditAction.RESTAURANT_REPORT_DISMISSED
                RestaurantInfoReportDecision.RESOLVE -> ModerationAuditAction.RESTAURANT_INFO_CORRECTED
            }
            assertThat(result.status).isEqualTo(ReportStatus.RESOLVED)
            assertThat(result.decision).isEqualTo(decision)
            assertAudit(fixture.audits.commands.single(), expectedAction)
        }
    }

    @Test
    fun `service revalidates active reporter and admin roles in persistence`() {
        val inactiveReporter = fixture(reviewTarget(), activeReporter = false)
        assertThatThrownBy {
            inactiveReporter.service.createReviewReport(
                CreateReviewReportCommand(REPORTER_ID, REVIEW_ID, ReviewReportReason.OTHER, null),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.errorCode).isEqualTo(ApiErrorCode.ACCESS_DENIED)
        }

        val nonAdmin = fixture(reviewTarget(), activeAdmin = false).also {
            it.reviewReports.seedPending()
        }
        assertThatThrownBy {
            nonAdmin.service.decideReviewReport(
                DecideReviewReportCommand(REPORTER_ID, REVIEW_REPORT_ID, ReviewReportDecision.DISMISS, null),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.errorCode).isEqualTo(ApiErrorCode.ACCESS_DENIED)
        }
    }

    @Test
    fun `all report writes and decisions declare transaction boundaries`() {
        listOf(
            "createReviewReport" to CreateReviewReportCommand::class.java,
            "createRestaurantInfoReport" to CreateRestaurantInfoReportCommand::class.java,
            "decideReviewReport" to DecideReviewReportCommand::class.java,
            "decideRestaurantInfoReport" to DecideRestaurantInfoReportCommand::class.java,
        ).forEach { (methodName, parameterType) ->
            val annotation = ReportingService::class.java.getMethod(methodName, parameterType)
                .getAnnotation(Transactional::class.java)
            assertThat(annotation).isNotNull
            assertThat(annotation.readOnly).isFalse()
        }

        listOf(
            "list" to ListPendingReviewReportsQuery::class.java,
            "list" to ListPendingRestaurantInfoReportsQuery::class.java,
        ).forEach { (methodName, parameterType) ->
            val annotation = ReportingService::class.java.getMethod(methodName, parameterType)
                .getAnnotation(Transactional::class.java)
            assertThat(annotation).isNotNull
            assertThat(annotation.readOnly).isTrue()
        }
    }

    private fun fixture(
        target: StoredReviewReportTarget,
        activeReporter: Boolean = true,
        activeAdmin: Boolean = true,
    ): Fixture {
        val reviewReports = FakeReviewReportRepository()
        val restaurantReports = FakeRestaurantInfoReportRepository()
        val targets = FakeReviewReportTargetRepository(target)
        val audits = FakeAuditRepository()
        val service = ReportingService(
            reporters = ModerationReporterRepository { activeReporter },
            admins = ModerationAdminRepository { activeAdmin },
            reviewReports = reviewReports,
            restaurantReports = restaurantReports,
            targets = targets,
            audits = audits,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )
        return Fixture(service, reviewReports, restaurantReports, targets, audits)
    }

    private fun reviewTarget(
        commentStatus: ReviewCommentStatus = ReviewCommentStatus.NONE,
        currentReviewId: Long? = REVIEW_ID,
        lastSubmittedAt: Instant = LAST_SUBMITTED_AT,
        lastSequence: Long = 1,
    ) = StoredReviewReportTarget(
        reviewId = REVIEW_ID,
        authorUserId = AUTHOR_ID,
        restaurantId = RESTAURANT_ID,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        commentStatus = commentStatus,
        currentReviewId = currentReviewId,
        lastSubmittedAt = lastSubmittedAt,
        lastSequence = lastSequence,
    )

    private fun assertAudit(command: ModerationAuditPersistenceCommand, action: ModerationAuditAction) {
        assertThat(command.actorUserId).isEqualTo(ADMIN_ID)
        assertThat(command.action).isEqualTo(action)
        assertThat(command.reason).isIn("위반 아님", "도배", "확인 완료", null)
        assertThat(command.occurredAt).isEqualTo(NOW)
        assertThat(command.beforeState).isNotBlank()
        assertThat(command.afterState).isNotBlank()
    }

    private data class Fixture(
        val service: ReportingService,
        val reviewReports: FakeReviewReportRepository,
        val restaurantReports: FakeRestaurantInfoReportRepository,
        val targets: FakeReviewReportTargetRepository,
        val audits: FakeAuditRepository,
    )

    private class FakeReviewReportRepository : ReviewReportRepository {
        val created = mutableListOf<NewReviewReportPersistenceCommand>()
        var duplicate = false
        var recentCount = 0L
        var lastCountSince: Instant? = null
        var otherPendingForReview = false
        private var stored: StoredReviewReport? = null

        fun seedPending() {
            stored = reviewReport()
        }

        override fun create(command: NewReviewReportPersistenceCommand): StoredReviewReport {
            created += command
            return reviewReport().also { stored = it }
        }

        override fun existsByReporterUserIdAndReviewId(reporterUserId: Long, reviewId: Long) = duplicate

        override fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long {
            lastCountSince = since
            return recentCount
        }

        override fun existsOtherPendingByReviewId(reviewId: Long, excludedReportId: Long) =
            otherPendingForReview

        override fun findPending(cursor: com.ridervoice.api.moderation.application.port.out.ModerationCursor?, limit: Int) =
            listOfNotNull(stored)

        override fun findPendingForUpdate(reportId: Long) =
            stored?.takeIf { it.reportId == reportId && it.status == ReportStatus.PENDING }

        override fun findForUpdate(reportId: Long) = stored?.takeIf { it.reportId == reportId }

        override fun saveDecision(command: ReviewReportDecisionPersistenceCommand): StoredReviewReport {
            val current = requireNotNull(stored)
            return current.copy(
                status = ReportStatus.RESOLVED,
                decision = command.decision,
                decidedByUserId = command.decidedByUserId,
                decidedAt = command.decidedAt,
            ).also { stored = it }
        }
    }

    private class FakeRestaurantInfoReportRepository : RestaurantInfoReportRepository {
        val created = mutableListOf<NewRestaurantInfoReportPersistenceCommand>()
        var duplicate = false
        var recentCount = 0L
        var lastCountSince: Instant? = null
        private var stored: StoredRestaurantInfoReport? = null

        fun seedPending() {
            stored = restaurantReport()
        }

        override fun create(command: NewRestaurantInfoReportPersistenceCommand): StoredRestaurantInfoReport {
            created += command
            return restaurantReport().also { stored = it }
        }

        override fun existsByReporterUserIdAndRestaurantId(reporterUserId: Long, restaurantId: Long) = duplicate

        override fun countByReporterUserIdSince(reporterUserId: Long, since: Instant): Long {
            lastCountSince = since
            return recentCount
        }

        override fun findPending(cursor: com.ridervoice.api.moderation.application.port.out.ModerationCursor?, limit: Int) =
            listOfNotNull(stored)

        override fun findPendingForUpdate(reportId: Long) =
            stored?.takeIf { it.reportId == reportId && it.status == ReportStatus.PENDING }

        override fun findForUpdate(reportId: Long) = stored?.takeIf { it.reportId == reportId }

        override fun saveDecision(
            command: RestaurantInfoReportDecisionPersistenceCommand,
        ): StoredRestaurantInfoReport {
            val current = requireNotNull(stored)
            return current.copy(
                status = ReportStatus.RESOLVED,
                decision = command.decision,
                decidedByUserId = command.decidedByUserId,
                decidedAt = command.decidedAt,
            ).also { stored = it }
        }
    }

    private class FakeReviewReportTargetRepository(
        initial: StoredReviewReportTarget,
    ) : ReviewReportTargetRepository {
        var current = initial
        val historyReviewIds = listOf(REVIEW_ID - 1)

        override fun findReviewForUpdate(reviewId: Long) = current.takeIf { it.reviewId == reviewId }

        override fun activeRestaurantExists(restaurantId: Long) = restaurantId == RESTAURANT_ID

        override fun mutate(command: ReviewReportTargetMutationCommand): StoredReviewReportTarget {
            check(current.visibilityStatus == command.expectedVisibilityStatus)
            check(current.commentStatus == command.expectedCommentStatus)
            current = current.copy(
                visibilityStatus = command.nextVisibilityStatus,
                commentStatus = command.nextCommentStatus,
                currentReviewId = if (command.clearCurrentPointerIfTarget && current.currentReviewId == current.reviewId) {
                    null
                } else {
                    current.currentReviewId
                },
            )
            return current
        }

        fun currentAggregateReviewIds(): List<Long> =
            if (
                current.visibilityStatus == ReviewVisibilityStatus.ACTIVE &&
                current.currentReviewId == current.reviewId
            ) {
                listOf(current.reviewId)
            } else {
                emptyList()
            }
    }

    private class FakeAuditRepository : ModerationAuditRepository {
        val commands = mutableListOf<ModerationAuditPersistenceCommand>()

        override fun append(command: ModerationAuditPersistenceCommand): StoredModerationAudit {
            commands += command
            return StoredModerationAudit(
                auditId = commands.size.toLong(),
                actorUserId = command.actorUserId,
                action = command.action,
                targetType = command.targetType,
                targetId = command.targetId,
                reason = command.reason,
                beforeState = command.beforeState,
                afterState = command.afterState,
                occurredAt = command.occurredAt,
                createdAt = command.occurredAt,
            )
        }
    }

    private companion object {
        const val REPORTER_ID = 7L
        const val ADMIN_ID = 8L
        const val AUTHOR_ID = 9L
        const val REVIEW_ID = 40L
        const val RESTAURANT_ID = 50L
        const val REVIEW_REPORT_ID = 101L
        const val RESTAURANT_REPORT_ID = 202L
        val NOW: Instant = Instant.parse("2026-07-26T03:00:00Z")
        val LAST_SUBMITTED_AT: Instant = NOW.minusSeconds(30L * 86_400)

        fun reviewReport() = StoredReviewReport(
            reportId = REVIEW_REPORT_ID,
            reporterUserId = REPORTER_ID,
            reviewId = REVIEW_ID,
            reason = ReviewReportReason.SPAM,
            details = null,
            status = ReportStatus.PENDING,
            decision = null,
            decidedByUserId = null,
            decidedAt = null,
            createdAt = NOW,
        )

        fun restaurantReport() = StoredRestaurantInfoReport(
            reportId = RESTAURANT_REPORT_ID,
            reporterUserId = REPORTER_ID,
            restaurantId = RESTAURANT_ID,
            reason = RestaurantInfoReportReason.DUPLICATE,
            details = null,
            status = ReportStatus.PENDING,
            decision = null,
            decidedByUserId = null,
            decidedAt = null,
            createdAt = NOW,
        )
    }
}
