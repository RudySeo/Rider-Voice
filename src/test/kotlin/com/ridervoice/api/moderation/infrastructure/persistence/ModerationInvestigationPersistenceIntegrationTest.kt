package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.moderation.application.port.out.ModerationInvestigationQuery
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
@Tag("integration")
class ModerationInvestigationPersistenceIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var investigation: ModerationInvestigationQuery

    @Test
    fun `admin investigation queries expose review restaurant references pending reports and filtered audits`() {
        val suffix = UUID.randomUUID().toString()
        val user = User().also {
            User::class.java.getDeclaredField("role").also { field ->
                field.isAccessible = true
                field.set(it, UserRole.ADMIN)
            }
            entityManager.persist(it)
        }
        val location = PickupLocation(
            standardAddress = "서울 강남구 조사로 $suffix",
            detailAddress = "지하 픽업대",
            latitude = BigDecimal("37.50000000"),
            longitude = BigDecimal("127.00000000"),
            source = PickupLocationSource.MANUAL_ADDRESS,
        ).also(entityManager::persist)
        val restaurant = Restaurant("조사 브랜드 $suffix", location, "place-$suffix")
            .also(entityManager::persist)
        entityManager.persist(RestaurantPlatform(restaurant, DeliveryPlatform.BAEMIN))
        val review = Review(
            author = user,
            restaurant = restaurant,
            visitMonth = VisitMonth.parse("2026-07"),
            ratings = ratings(),
            comment = "관리자 조사 원문",
        ).also(entityManager::persist)
        entityManager.persist(
            RestaurantInfoReport(user, restaurant, RestaurantInfoReportReason.INCORRECT_NAME, "확인 필요"),
        )
        val audit = ModerationAudit(
            actor = user,
            action = ModerationAuditAction.RESTAURANT_RENAMED,
            targetType = ModerationTargetType.RESTAURANT,
            targetId = restaurant.id,
            reason = "조사 감사",
            beforeState = "before",
            afterState = "after",
            occurredAt = Instant.parse("2026-07-21T00:00:00Z"),
        ).also(entityManager::persist)
        entityManager.flush()
        entityManager.clear()

        val storedReview = investigation.findReview(review.id)
        assertThat(storedReview?.comment).isEqualTo("관리자 조사 원문")
        assertThat(storedReview?.active).isTrue()
        assertThat(storedReview?.deletedAt).isNull()
        assertThat(storedReview?.publicReviewCount).isEqualTo(1L)

        val search = investigation.searchRestaurants("조사 브랜드", RestaurantStatus.ACTIVE, null, 20)
        assertThat(search.map { it.restaurantId }).contains(restaurant.id)

        val storedRestaurant = investigation.findRestaurant(restaurant.id)
        assertThat(storedRestaurant?.kakaoPlaceId).isEqualTo("place-$suffix")
        assertThat(storedRestaurant?.platforms).containsExactly(DeliveryPlatform.BAEMIN)
        assertThat(storedRestaurant?.pendingReportCount).isEqualTo(1L)

        val audits = investigation.findAudits(
            targetType = ModerationTargetType.RESTAURANT,
            targetId = restaurant.id,
            actorUserId = user.id,
            action = ModerationAuditAction.RESTAURANT_RENAMED,
            cursor = null,
            limit = 20,
        )
        assertThat(audits.map { it.auditId }).containsExactly(audit.id)
        assertThat(audits.single().beforeState).isEqualTo("before")
    }

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )
}
