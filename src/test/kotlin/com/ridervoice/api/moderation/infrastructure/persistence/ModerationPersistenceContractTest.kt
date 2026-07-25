package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationCursor
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewReportTargetMutationCommand
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReport
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class ModerationPersistenceContractTest {

    @Test
    fun `report mappings own reporter target uniqueness queue indexes and no cascades`() {
        assertReportMapping(
            ReviewReport::class.java,
            "uk_review_reports_reporter_review",
            arrayOf("reporter_user_id", "review_id"),
            "idx_review_reports_status_created" to "status, created_at, id",
            "review",
        )
        assertReportMapping(
            RestaurantInfoReport::class.java,
            "uk_restaurant_info_reports_reporter_restaurant",
            arrayOf("reporter_user_id", "restaurant_id"),
            "idx_restaurant_info_reports_status_created" to "status, created_at, id",
            "restaurant",
        )

        assertThat(ModerationAudit::class.java.superclass).isEqualTo(BaseEntity::class.java)
        assertLazyNoCascade(ModerationAudit::class.java, "actor", optional = false)
        assertThat(ModerationAudit::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    @Test
    fun `pending decision lookups use pessimistic write locks`() {
        assertThat(
            SpringDataReviewReportRepository::class.java
                .getMethod("findPendingForUpdate", java.lang.Long.TYPE, ReportStatus::class.java)
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
        assertThat(
            SpringDataRestaurantInfoReportRepository::class.java
                .getMethod("findPendingForUpdate", java.lang.Long.TYPE, ReportStatus::class.java)
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }

    @Test
    fun `review report adapter supports creation duplicate checks cursor queue and one decision`() {
        val fixture = fixture()
        val report = ReviewReport(
            reporter = fixture.reporter,
            review = fixture.review,
            reason = ReviewReportReason.FALSE_INFORMATION,
            details = "상세 사유",
        ).also {
            it.id = 101L
            setAuditTimes(it, fixture.createdAt)
        }
        val repository = fakeRepository(SpringDataReviewReportRepository::class.java) { method, arguments ->
            when (method.name) {
                "saveAndFlush" -> report
                "existsByReporterIdAndReviewId" -> true
                "countByReporterIdAndCreatedAtGreaterThanEqual" -> 4L
                "findAllPending" -> {
                    assertPageable(arguments.last(), 5)
                    listOf(report)
                }
                "findAllPendingBeforeCursor" -> {
                    assertThat(arguments[1]).isEqualTo(fixture.createdAt)
                    assertThat(arguments[2]).isEqualTo(101L)
                    assertPageable(arguments.last(), 5)
                    listOf(report)
                }
                "findPendingForUpdate" -> Optional.of(report)
                else -> unexpected(method)
            }
        }
        val adapter = ReviewReportPersistenceAdapter(repository, fixture.entityManager)

        assertThat(adapter.create(NewReviewReportPersistenceCommand(7L, 40L, report.reason, report.details)).reportId)
            .isEqualTo(101L)
        assertThat(adapter.existsByReporterUserIdAndReviewId(7L, 40L)).isTrue()
        assertThat(adapter.countByReporterUserIdSince(7L, fixture.createdAt.minusSeconds(60))).isEqualTo(4L)
        assertThat(adapter.findPending(null, 5)).hasSize(1)
        assertThat(adapter.findPending(ModerationCursor(fixture.createdAt, 101L), 5)).hasSize(1)
        assertThat(adapter.findPendingForUpdate(101L)?.status).isEqualTo(ReportStatus.PENDING)

        val resolved = adapter.saveDecision(
            ReviewReportDecisionPersistenceCommand(
                reportId = 101L,
                decision = ReviewReportDecision.EXCLUDE_REVIEW,
                decidedByUserId = 8L,
                decidedAt = fixture.decidedAt,
            ),
        )

        assertThat(resolved.status).isEqualTo(ReportStatus.RESOLVED)
        assertThat(resolved.decision).isEqualTo(ReviewReportDecision.EXCLUDE_REVIEW)
        assertThat(resolved.decidedByUserId).isEqualTo(8L)
        assertThat(resolved.decidedAt).isEqualTo(fixture.decidedAt)
    }

    @Test
    fun `restaurant report and audit adapters preserve decisions and immutable audit context`() {
        val fixture = fixture()
        val report = RestaurantInfoReport(
            reporter = fixture.reporter,
            restaurant = fixture.restaurant,
            reason = RestaurantInfoReportReason.DUPLICATE,
            details = null,
        ).also {
            it.id = 202L
            setAuditTimes(it, fixture.createdAt)
        }
        val reports = fakeRepository(SpringDataRestaurantInfoReportRepository::class.java) { method, arguments ->
            when (method.name) {
                "saveAndFlush" -> report
                "existsByReporterIdAndRestaurantId" -> false
                "countByReporterIdAndCreatedAtGreaterThanEqual" -> 2L
                "findAllPending" -> listOf(report)
                "findAllPendingBeforeCursor" -> listOf(report)
                "findPendingForUpdate" -> Optional.of(report)
                else -> unexpected(method)
            }
        }
        val audit = ModerationAudit(
            actor = fixture.admin,
            action = ModerationAuditAction.DUPLICATE_RESTAURANT_MERGED,
            targetType = ModerationTargetType.RESTAURANT,
            targetId = fixture.restaurant.id,
            reason = "중복 확인",
            beforeState = "{\"status\":\"ACTIVE\"}",
            afterState = "{\"status\":\"MERGED\"}",
            occurredAt = fixture.decidedAt,
        ).also {
            it.id = 303L
            setAuditTimes(it, fixture.createdAt)
        }
        val audits = fakeRepository(SpringDataModerationAuditRepository::class.java) { method, _ ->
            when (method.name) {
                "saveAndFlush" -> audit
                else -> unexpected(method)
            }
        }
        val reportAdapter = RestaurantInfoReportPersistenceAdapter(reports, fixture.entityManager)
        val auditAdapter = ModerationAuditPersistenceAdapter(audits, fixture.entityManager)

        assertThat(
            reportAdapter.create(
                NewRestaurantInfoReportPersistenceCommand(
                    reporterUserId = 7L,
                    restaurantId = fixture.restaurant.id,
                    reason = RestaurantInfoReportReason.DUPLICATE,
                    details = null,
                ),
            ).reportId,
        ).isEqualTo(202L)
        assertThat(reportAdapter.existsByReporterUserIdAndRestaurantId(7L, fixture.restaurant.id)).isFalse()
        assertThat(reportAdapter.countByReporterUserIdSince(7L, fixture.createdAt.minusSeconds(60))).isEqualTo(2L)
        assertThat(reportAdapter.findPending(null, 5)).hasSize(1)
        assertThat(reportAdapter.findPending(ModerationCursor(fixture.createdAt, 202L), 5)).hasSize(1)

        val resolved = reportAdapter.saveDecision(
            RestaurantInfoReportDecisionPersistenceCommand(
                reportId = 202L,
                decision = RestaurantInfoReportDecision.RESOLVE,
                decidedByUserId = 8L,
                decidedAt = fixture.decidedAt,
            ),
        )
        assertThat(resolved.status).isEqualTo(ReportStatus.RESOLVED)
        assertThat(resolved.decision).isEqualTo(RestaurantInfoReportDecision.RESOLVE)

        val storedAudit = auditAdapter.append(
            ModerationAuditPersistenceCommand(
                actorUserId = 8L,
                action = audit.action,
                targetType = audit.targetType,
                targetId = audit.targetId,
                reason = audit.reason,
                beforeState = audit.beforeState,
                afterState = audit.afterState,
                occurredAt = audit.occurredAt,
            ),
        )
        assertThat(storedAudit.auditId).isEqualTo(303L)
        assertThat(storedAudit.actorUserId).isEqualTo(8L)
        assertThat(storedAudit.beforeState).isEqualTo("{\"status\":\"ACTIVE\"}")
        assertThat(storedAudit.afterState).isEqualTo("{\"status\":\"MERGED\"}")
        assertThat(storedAudit.occurredAt).isEqualTo(fixture.decidedAt)
    }

    @Test
    fun `review report target adapter hides comment then excludes without cooldown or history fallback`() {
        val fixture = fixture()
        val reviewRepository = fakeRepository(SpringDataModerationReviewTargetRepository::class.java) {
                method,
                arguments,
            ->
            when (method.name) {
                "findByIdForUpdate" -> Optional.of(fixture.review)
                "saveAndFlush" -> arguments.single()
                else -> unexpected(method)
            }
        }
        val stateRepository = fakeRepository(SpringDataModerationReviewStateRepository::class.java) {
                method,
                arguments,
            ->
            when (method.name) {
                "findForUpdate" -> Optional.of(fixture.state)
                "saveAndFlush" -> arguments.single()
                else -> unexpected(method)
            }
        }
        val restaurantRepository = fakeRepository(
            SpringDataModerationRestaurantTargetRepository::class.java,
        ) { method, _ ->
            when (method.name) {
                "existsByIdAndStatus" -> true
                else -> unexpected(method)
            }
        }
        val adapter = ReviewReportTargetPersistenceAdapter(
            reviewRepository,
            stateRepository,
            restaurantRepository,
        )
        val initial = adapter.findReviewForUpdate(fixture.review.id)!!

        val hidden = adapter.mutate(
            ReviewReportTargetMutationCommand(
                reviewId = fixture.review.id,
                expectedVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                nextVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                expectedCommentStatus = ReviewCommentStatus.PUBLISHED,
                nextCommentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                clearCurrentPointerIfTarget = false,
            ),
        )
        val excluded = adapter.mutate(
            ReviewReportTargetMutationCommand(
                reviewId = fixture.review.id,
                expectedVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                nextVisibilityStatus = ReviewVisibilityStatus.EXCLUDED,
                expectedCommentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                nextCommentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                clearCurrentPointerIfTarget = true,
            ),
        )

        assertThat(initial.currentReviewId).isEqualTo(fixture.review.id)
        assertThat(hidden.commentStatus).isEqualTo(ReviewCommentStatus.HIDDEN_REPORTED)
        assertThat(hidden.visibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertThat(excluded.visibilityStatus).isEqualTo(ReviewVisibilityStatus.EXCLUDED)
        assertThat(excluded.currentReviewId).isNull()
        assertThat(excluded.lastSubmittedAt).isEqualTo(fixture.state.lastSubmittedAt)
        assertThat(excluded.lastSequence).isEqualTo(fixture.state.lastSequence)
    }

    private fun assertReportMapping(
        type: Class<*>,
        uniqueName: String,
        uniqueColumns: Array<String>,
        expectedIndex: Pair<String, String>,
        targetField: String,
    ) {
        assertThat(type.superclass).isEqualTo(BaseEntity::class.java)
        val table = type.getAnnotation(Table::class.java)
        val unique = table.uniqueConstraints.single { it.name == uniqueName }
        assertThat(unique.columnNames).containsExactly(*uniqueColumns)
        assertThat(table.indexes.associate { it.name to it.columnList })
            .containsEntry(expectedIndex.first, expectedIndex.second)
        assertLazyNoCascade(type, "reporter", optional = false)
        assertLazyNoCascade(type, targetField, optional = false)
        assertLazyNoCascade(type, "decidedBy", optional = true)
        assertThat(type.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    private fun assertLazyNoCascade(type: Class<*>, fieldName: String, optional: Boolean) {
        val relation = type.getDeclaredField(fieldName).getAnnotation(ManyToOne::class.java)
        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isEqualTo(optional)
        assertThat(relation.cascade).isEmpty()
    }

    private fun fixture(): Fixture {
        val reporter = User().also { it.id = 7L }
        val admin = User().also { it.id = 8L }
        val location = PickupLocation(
            standardAddress = "서울 강남구 테헤란로 1",
            detailAddress = null,
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            source = PickupLocationSource.KAKAO,
        ).also { it.id = 20L }
        val restaurant = Restaurant("브랜드", location).also { it.id = 30L }
        val review = Review(
            author = reporter,
            restaurant = restaurant,
            visitMonth = VisitMonth.parse("2026-07"),
            ratings = ReviewRatings(
                pickupSpaceCleanliness = ReviewRating.GOOD,
                packagingStability = ReviewRating.GOOD,
                orderReadiness = ReviewRating.GOOD,
                handoffAccuracy = ReviewRating.GOOD,
                staffInteraction = ReviewRating.NOT_OBSERVED,
                riderRespect = ReviewRating.GOOD,
            ),
            comment = "공개된 의견",
            sequence = 1L,
        ).also {
            it.id = 40L
            it.publishComment()
        }
        val state = AuthorRestaurantReviewState(
            author = reporter,
            restaurant = restaurant,
            lastSubmittedAt = Instant.parse("2026-07-25T03:00:00Z"),
            lastSequence = 1L,
            currentReview = review,
        ).also { it.id = 50L }
        val entityManager = fakeRepository(EntityManager::class.java) { method, arguments ->
            when (method.name) {
                "getReference" -> when (arguments[0]) {
                    User::class.java -> if (arguments[1] == 7L) reporter else admin
                    Review::class.java -> review
                    Restaurant::class.java -> restaurant
                    else -> unexpected(method)
                }
                else -> unexpected(method)
            }
        }
        return Fixture(
            reporter,
            admin,
            restaurant,
            review,
            state,
            entityManager,
            Instant.parse("2026-07-25T03:00:00Z"),
            Instant.parse("2026-07-26T04:00:00Z"),
        )
    }

    private fun assertPageable(value: Any?, size: Int) {
        assertThat(value).isInstanceOf(Pageable::class.java)
        assertThat((value as Pageable).pageSize).isEqualTo(size)
    }

    private fun setAuditTimes(entity: BaseEntity, instant: Instant) {
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            BaseEntity::class.java.getDeclaredField(fieldName).also {
                it.isAccessible = true
                it.set(entity, instant)
            }
        }
    }

    private fun <T> fakeRepository(type: Class<T>, handler: (Method, List<Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Fake${type.simpleName}"
                else -> handler(method, arguments?.toList().orEmpty())
            }
        },
    )

    private fun unexpected(method: Method): Nothing = error("Unexpected method: ${method.name}")

    private data class Fixture(
        val reporter: User,
        val admin: User,
        val restaurant: Restaurant,
        val review: Review,
        val state: AuthorRestaurantReviewState,
        val entityManager: EntityManager,
        val createdAt: Instant,
        val decidedAt: Instant,
    )
}
