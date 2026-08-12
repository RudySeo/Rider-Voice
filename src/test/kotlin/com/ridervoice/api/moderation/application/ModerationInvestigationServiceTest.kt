package com.ridervoice.api.moderation.application

import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailQuery
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsQuery
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationInvestigationQuery
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurantDetail
import com.ridervoice.api.moderation.application.port.out.StoredAdminReviewDetail
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ModerationInvestigationServiceTest {

    @Test
    fun `active admin receives review context without external account data`() {
        val service = ModerationInvestigationService(
            ModerationAdminRepository { true },
            FakeQuery(review = review()),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        val result = service.get(GetAdminReviewDetailQuery(ADMIN_ID, 40L))

        assertThat(result.reviewId).isEqualTo(40L)
        assertThat(result.authorUserId).isEqualTo(9L)
        assertThat(result.authorActivityMonths).isEqualTo(3)
        assertThat(result.restaurantName).isEqualTo("브랜드")
        assertThat(result.comment).isEqualTo("신고 조사 원문")
    }

    @Test
    fun `restaurant search normalizes query paginates and rejects non-admin`() {
        val rows = listOf(restaurant(3L), restaurant(2L), restaurant(1L))
        val query = FakeQuery(restaurants = rows)
        val service = ModerationInvestigationService(
            ModerationAdminRepository { it == ADMIN_ID },
            query,
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        val result = service.search(SearchAdminRestaurantsQuery(ADMIN_ID, "  브랜드  ", null, null, 2))

        assertThat(query.normalizedQuery).isEqualTo("브랜드")
        assertThat(result.items.map { it.restaurantId }).containsExactly(3L, 2L)
        assertThat(result.nextCursor).isEqualTo(AdminRestaurantCursor(rows[1].createdAt, 2L))
        assertThatThrownBy {
            service.search(SearchAdminRestaurantsQuery(7L, "브랜드", null, null, 2))
        }.isInstanceOf(ApiException::class.java)
    }

    private class FakeQuery(
        private val review: StoredAdminReviewDetail? = null,
        private val restaurants: List<StoredAdminRestaurantDetail> = emptyList(),
    ) : ModerationInvestigationQuery {
        var normalizedQuery: String? = null

        override fun findReview(reviewId: Long) = review
        override fun searchRestaurants(normalizedQuery: String, status: RestaurantStatus?, cursor: AdminRestaurantCursor?, limit: Int): List<StoredAdminRestaurantDetail> {
            this.normalizedQuery = normalizedQuery
            return restaurants.take(limit)
        }
        override fun findRestaurant(restaurantId: Long) = restaurants.firstOrNull { it.restaurantId == restaurantId }
        override fun findAudits(targetType: ModerationTargetType?, targetId: Long?, actorUserId: Long?, action: ModerationAuditAction?, cursor: com.ridervoice.api.moderation.application.model.ModerationAuditCursor?, limit: Int): List<StoredModerationAudit> = emptyList()
    }

    private fun review() = StoredAdminReviewDetail(
        reviewId = 40L,
        authorUserId = 9L,
        authorStatus = UserStatus.ACTIVE,
        firstPublicReviewAt = Instant.parse("2026-05-01T00:00:00Z"),
        publicReviewCount = 4,
        restaurantId = 20L,
        restaurantName = "브랜드",
        restaurantStatus = RestaurantStatus.ACTIVE,
        pickupLocationId = 30L,
        pickupAddress = "서울 강남구 1",
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ReviewRatings(ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD),
        comment = "신고 조사 원문",
        commentStatus = ReviewCommentStatus.PENDING,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        active = true,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun restaurant(id: Long) = StoredAdminRestaurantDetail(
        restaurantId = id,
        name = "브랜드-$id",
        status = RestaurantStatus.ACTIVE,
        canonicalRestaurantId = null,
        pickupLocationId = 100L + id,
        standardAddress = "서울 $id",
        detailAddress = null,
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        externalReferences = emptyList(),
        platforms = emptySet(),
        pendingReportCount = 0,
        createdAt = NOW.minusSeconds(id),
        updatedAt = NOW,
    )

    private companion object {
        const val ADMIN_ID = 8L
        val NOW: Instant = Instant.parse("2026-07-26T00:00:00Z")
    }
}
