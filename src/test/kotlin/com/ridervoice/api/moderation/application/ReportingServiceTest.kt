package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCorrection
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantCorrection
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
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.review.application.ReviewAggregateService
import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
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
    fun `exclude releases active slot removes aggregate input and retains submission time`() {
        val fixture = fixture(
            reviewTarget(
                commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                submittedAt = LAST_SUBMITTED_AT,
            ),
        ).also { it.reviewReports.seedPending() }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.EXCLUDE_REVIEW, "도배"),
        )

        assertThat(fixture.targets.current.visibilityStatus).isEqualTo(ReviewVisibilityStatus.EXCLUDED)
        assertThat(fixture.targets.current.active).isFalse()
        assertThat(fixture.targets.current.submittedAt).isEqualTo(LAST_SUBMITTED_AT)
        assertThat(fixture.targets.currentAggregateReviewIds()).isEmpty()
        assertThat(fixture.targets.historyReviewIds).containsExactly(REVIEW_ID - 1)
        assertAudit(fixture.audits.commands.single(), ModerationAuditAction.REVIEW_EXCLUDED)
    }

    @Test
    fun `exclude automatically resolves every other pending report for the excluded review`() {
        val fixture = fixture(reviewTarget(commentStatus = ReviewCommentStatus.HIDDEN_REPORTED)).also {
            it.reviewReports.seedPending()
            it.reviewReports.siblingReports += reviewReport().copy(reportId = REVIEW_REPORT_ID + 1)
            it.reviewReports.siblingReports += reviewReport().copy(reportId = REVIEW_REPORT_ID + 2)
        }

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.EXCLUDE_REVIEW, "도배"),
        )

        assertThat(fixture.reviewReports.savedDecisionCommands.map { it.reportId }).containsExactly(
            REVIEW_REPORT_ID,
            REVIEW_REPORT_ID + 1,
            REVIEW_REPORT_ID + 2,
        )
        assertThat(fixture.reviewReports.savedDecisionCommands.map { it.decision })
            .containsOnly(ReviewReportDecision.EXCLUDE_REVIEW)
        assertThat(fixture.audits.commands).hasSize(3)
        assertThat(fixture.audits.commands.drop(1).map { it.reason })
            .containsOnly("AUTO_RESOLVED_TARGET_EXCLUDED")
    }

    @Test
    fun `full exclusion drops five distinct aggregate authors back to collecting`() {
        val fixture = fixture(
            reviewTarget(submittedAt = LAST_SUBMITTED_AT),
        ).also { it.reviewReports.seedPending() }
        val unaffected = (1L..4L).map { authorId -> aggregateInput(authorId, authorId) }
        val aggregateService = ReviewAggregateService(
            object : AggregateReviewQuery {
                override fun findCurrentActiveByRestaurantId(restaurantId: Long) =
                    unaffected + fixture.targets.currentAggregateInputs()

                override fun findLatestCurrentActiveByPickupLocationId(pickupLocationId: Long) =
                    unaffected + fixture.targets.currentAggregateInputs()
            },
        )

        assertThat(aggregateService.getBrandReport(RESTAURANT_ID).status)
            .isEqualTo(AggregationStatus.PUBLISHED)
        assertThat(aggregateService.getPickupLocationReport(60L).status)
            .isEqualTo(AggregationStatus.PUBLISHED)

        fixture.service.decideReviewReport(
            DecideReviewReportCommand(
                ADMIN_ID,
                REVIEW_REPORT_ID,
                ReviewReportDecision.EXCLUDE_REVIEW,
                "허위·도배 확인",
            ),
        )

        val brand = aggregateService.getBrandReport(RESTAURANT_ID)
        val location = aggregateService.getPickupLocationReport(60L)
        assertThat(brand.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(brand.contributorCount).isEqualTo(4)
        assertThat(brand.metrics).isNull()
        assertThat(location.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(location.contributorCount).isEqualTo(4)
        assertThat(location.metrics).isNull()
    }

    @Test
    fun `decision rejects an already inactive review`() {
        val fixture = fixture(reviewTarget(active = false)).also {
            it.reviewReports.seedPending()
        }

        assertThatThrownBy {
            fixture.service.decideReviewReport(
                DecideReviewReportCommand(ADMIN_ID, REVIEW_REPORT_ID, ReviewReportDecision.EXCLUDE_REVIEW, null),
            )
        }.isInstanceOf(StateConflictException::class.java)
        assertThat(fixture.targets.current.active).isFalse()
    }

    @Test
    fun `restaurant information report decisions resolve once and append matching audits`() {
        RestaurantInfoReportDecision.entries.forEach { decision ->
            val fixture = fixture(reviewTarget()).also { it.restaurantReports.seedPending() }

            val result = fixture.restaurantDecision.decideRestaurantInfoReport(
                DecideRestaurantInfoReportCommand(
                    ADMIN_ID,
                    RESTAURANT_REPORT_ID,
                    decision,
                    "확인 완료",
                    if (decision == RestaurantInfoReportDecision.RESOLVE) {
                        RenameRestaurantCorrection("정정 브랜드")
                    } else {
                        null
                    },
                ),
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
    fun `merge correction automatically resolves sibling restaurant reports`() {
        val fixture = fixture(reviewTarget()).also {
            it.restaurantReports.seedPending()
            it.restaurantReports.siblingReports += restaurantReport().copy(reportId = RESTAURANT_REPORT_ID + 1)
        }

        fixture.restaurantDecision.decideRestaurantInfoReport(
            DecideRestaurantInfoReportCommand(
                ADMIN_ID,
                RESTAURANT_REPORT_ID,
                RestaurantInfoReportDecision.RESOLVE,
                "중복 병합",
                MergeRestaurantCorrection(99L),
            ),
        )

        assertThat(fixture.restaurantReports.savedDecisionCommands.map { it.reportId }).containsExactly(
            RESTAURANT_REPORT_ID,
            RESTAURANT_REPORT_ID + 1,
        )
        assertThat(fixture.audits.commands.last().reason).isEqualTo("AUTO_RESOLVED_TARGET_MERGED")
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
    fun `restaurant correction is prepared before entering the transactional coordinator`() {
        val events = mutableListOf<String>()
        val corrections = object : RestaurantInfoCorrectionExecutor {
            override fun prepare(correction: com.ridervoice.api.moderation.application.port.`in`.RestaurantInfoCorrectionCommand): PreparedRestaurantCorrection {
                events += "provider-validation"
                return PreparedRestaurantCorrection.Rename("정정 브랜드")
            }

            override fun execute(
                adminUserId: Long,
                restaurantId: Long,
                correction: PreparedRestaurantCorrection,
                reason: String?,
            ) = error("The facade must not execute the correction")
        }
        val service = RestaurantInfoReportDecisionService(
            corrections,
            PreparedRestaurantInfoReportDecisionCoordinator { _, _ ->
                events += "transactional-coordinator"
                error("coordinator reached")
            },
        )

        assertThatThrownBy {
            service.decideRestaurantInfoReport(
                DecideRestaurantInfoReportCommand(
                    ADMIN_ID,
                    RESTAURANT_REPORT_ID,
                    RestaurantInfoReportDecision.RESOLVE,
                    null,
                    RenameRestaurantCorrection("정정 브랜드"),
                ),
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(events).containsExactly("provider-validation", "transactional-coordinator")
        assertThat(
            RestaurantInfoReportDecisionService::class.java
                .getMethod("decideRestaurantInfoReport", DecideRestaurantInfoReportCommand::class.java)
                .getAnnotation(Transactional::class.java),
        ).isNull()
    }

    @Test
    fun `all report writes and decisions declare transaction boundaries`() {
        listOf(
            "createReviewReport" to CreateReviewReportCommand::class.java,
            "createRestaurantInfoReport" to CreateRestaurantInfoReportCommand::class.java,
            "decideReviewReport" to DecideReviewReportCommand::class.java,
        ).forEach { (methodName, parameterType) ->
            val annotation = ReportingService::class.java.getMethod(methodName, parameterType)
                .getAnnotation(Transactional::class.java)
            assertThat(annotation).isNotNull
            assertThat(annotation.readOnly).isFalse()
        }
        val restaurantDecision = ReportingService::class.java.getMethod(
            "decidePrepared",
            DecideRestaurantInfoReportCommand::class.java,
            PreparedRestaurantCorrection::class.java,
        ).getAnnotation(Transactional::class.java)
        assertThat(restaurantDecision).isNotNull
        assertThat(restaurantDecision.readOnly).isFalse()

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
        val corrections = object : RestaurantInfoCorrectionExecutor {
            override fun prepare(correction: com.ridervoice.api.moderation.application.port.`in`.RestaurantInfoCorrectionCommand) =
                if (correction is MergeRestaurantCorrection) {
                    PreparedRestaurantCorrection.Merge(correction.canonicalRestaurantId)
                } else {
                    PreparedRestaurantCorrection.Rename("정정 브랜드")
                }

            override fun execute(
                adminUserId: Long,
                restaurantId: Long,
                correction: PreparedRestaurantCorrection,
                reason: String?,
            ) = Unit
        }
        val service = ReportingService(
            reporters = ModerationReporterRepository { activeReporter },
            admins = ModerationAdminRepository { activeAdmin },
            reviewReports = reviewReports,
            restaurantReports = restaurantReports,
            targets = targets,
            audits = audits,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            corrections = corrections,
        )
        return Fixture(
            service,
            RestaurantInfoReportDecisionService(corrections, service),
            reviewReports,
            restaurantReports,
            targets,
            audits,
        )
    }

    private fun reviewTarget(
        commentStatus: ReviewCommentStatus = ReviewCommentStatus.NONE,
        active: Boolean = true,
        submittedAt: Instant = LAST_SUBMITTED_AT,
        deletedAt: Instant? = null,
    ) = StoredReviewReportTarget(
        reviewId = REVIEW_ID,
        authorUserId = AUTHOR_ID,
        restaurantId = RESTAURANT_ID,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        commentStatus = commentStatus,
        active = active,
        submittedAt = submittedAt,
        deletedAt = deletedAt,
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
        val restaurantDecision: DecideRestaurantInfoReportUseCase,
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
        val siblingReports = mutableListOf<StoredReviewReport>()
        val savedDecisionCommands = mutableListOf<ReviewReportDecisionPersistenceCommand>()
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

        override fun findOtherPendingForUpdate(reviewId: Long, excludedReportId: Long) =
            siblingReports.filter { it.reviewId == reviewId && it.reportId != excludedReportId }

        override fun saveDecision(command: ReviewReportDecisionPersistenceCommand): StoredReviewReport {
            savedDecisionCommands += command
            val current = if (stored?.reportId == command.reportId) {
                requireNotNull(stored)
            } else {
                siblingReports.first { it.reportId == command.reportId }
            }
            return current.copy(
                status = ReportStatus.RESOLVED,
                decision = command.decision,
                decidedByUserId = command.decidedByUserId,
                decidedAt = command.decidedAt,
            ).also { resolved ->
                if (stored?.reportId == resolved.reportId) stored = resolved
                val siblingIndex = siblingReports.indexOfFirst { it.reportId == resolved.reportId }
                if (siblingIndex >= 0) siblingReports[siblingIndex] = resolved
            }
        }
    }

    private class FakeRestaurantInfoReportRepository : RestaurantInfoReportRepository {
        val created = mutableListOf<NewRestaurantInfoReportPersistenceCommand>()
        var duplicate = false
        var recentCount = 0L
        var lastCountSince: Instant? = null
        private var stored: StoredRestaurantInfoReport? = null
        val siblingReports = mutableListOf<StoredRestaurantInfoReport>()
        val savedDecisionCommands = mutableListOf<RestaurantInfoReportDecisionPersistenceCommand>()

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

        override fun findOtherPendingForUpdate(restaurantId: Long, excludedReportId: Long) = siblingReports

        override fun saveDecision(
            command: RestaurantInfoReportDecisionPersistenceCommand,
        ): StoredRestaurantInfoReport {
            savedDecisionCommands += command
            val current = if (stored?.reportId == command.reportId) requireNotNull(stored)
            else siblingReports.first { it.reportId == command.reportId }
            return current.copy(
                status = ReportStatus.RESOLVED,
                decision = command.decision,
                decidedByUserId = command.decidedByUserId,
                decidedAt = command.decidedAt,
            ).also { resolved ->
                if (stored?.reportId == resolved.reportId) stored = resolved
                val index = siblingReports.indexOfFirst { it.reportId == resolved.reportId }
                if (index >= 0) siblingReports[index] = resolved
            }
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
                active = current.active && !command.clearCurrentPointerIfTarget,
            )
            return current
        }

        fun currentAggregateReviewIds(): List<Long> =
            if (
                current.visibilityStatus == ReviewVisibilityStatus.ACTIVE && current.active
            ) {
                listOf(current.reviewId)
            } else {
                emptyList()
            }

        fun currentAggregateInputs(): List<AggregateReviewInput> = currentAggregateReviewIds().map {
            aggregateInput(it, current.authorUserId)
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

        fun aggregateInput(reviewId: Long, authorUserId: Long) = AggregateReviewInput(
            reviewId = reviewId,
            authorUserId = authorUserId,
            ratings = ReviewRatings(
                pickupSpaceCleanliness = ReviewRating.GOOD,
                packagingStability = ReviewRating.GOOD,
                orderReadiness = ReviewRating.GOOD,
                handoffAccuracy = ReviewRating.GOOD,
                staffInteraction = ReviewRating.GOOD,
                riderRespect = ReviewRating.GOOD,
            ),
            createdAt = NOW.minusSeconds(reviewId),
        )

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
