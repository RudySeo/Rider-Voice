package com.ridervoice.api.review.application

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ReviewAggregateServiceTest {

    @Test
    fun `deleted and excluded current reviews omitted by the query produce no reviews`() {
        val service = service(brandInputs = emptyList(), locationInputs = emptyList())

        val brand = service.getBrandReport(10L)
        val location = service.getPickupLocationReport(20L)

        assertThat(brand.status).isEqualTo(AggregationStatus.NO_REVIEWS)
        assertThat(brand.contributorCount).isZero()
        assertThat(brand.metrics).isNull()
        assertThat(location.status).isEqualTo(AggregationStatus.NO_REVIEWS)
        assertThat(location.contributorCount).isZero()
        assertThat(location.metrics).isNull()
    }

    @Test
    fun `one and four distinct authors remain collecting without exposing metrics`() {
        val oneAuthor = listOf(input(reviewId = 1L, authorUserId = 1L))
        val fourAuthors = (1L..4L).map { authorId -> input(reviewId = authorId, authorUserId = authorId) }
        val service = service(brandInputs = oneAuthor, locationInputs = fourAuthors)

        val brand = service.getBrandReport(10L)
        val location = service.getPickupLocationReport(20L)

        assertThat(brand.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(brand.contributorCount).isEqualTo(1)
        assertThat(brand.metrics).isNull()
        assertThat(location.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(location.contributorCount).isEqualTo(4)
        assertThat(location.metrics).isNull()
    }

    @Test
    fun `five reviews from four authors do not meet the publication threshold`() {
        val inputs = listOf(
            input(1L, 1L, createdAt = "2026-07-25T03:00:00Z", packagingStability = ReviewRating.VERY_GOOD),
            input(2L, 1L, createdAt = "2026-07-26T03:00:00Z", packagingStability = ReviewRating.GOOD),
            input(3L, 2L),
            input(4L, 3L),
            input(5L, 4L),
        )
        val service = service(brandInputs = inputs)

        val result = service.getBrandReport(10L)

        assertThat(result.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(result.contributorCount).isEqualTo(4)
        assertThat(result.metrics).isNull()
    }

    @Test
    fun `five distinct authors publish three brand metrics independently`() {
        val inputs = listOf(
            input(1L, 1L, packagingStability = ReviewRating.VERY_GOOD, orderReadiness = ReviewRating.GOOD, handoffAccuracy = ReviewRating.NOT_OBSERVED),
            input(2L, 2L, packagingStability = ReviewRating.VERY_GOOD, orderReadiness = ReviewRating.NEEDS_IMPROVEMENT, handoffAccuracy = ReviewRating.NOT_OBSERVED),
            input(3L, 3L, packagingStability = ReviewRating.GOOD, orderReadiness = ReviewRating.GOOD, handoffAccuracy = ReviewRating.NOT_OBSERVED),
            input(4L, 4L, packagingStability = ReviewRating.NEEDS_IMPROVEMENT, orderReadiness = ReviewRating.GOOD, handoffAccuracy = ReviewRating.NOT_OBSERVED),
            input(5L, 5L, packagingStability = ReviewRating.MAJOR_IMPROVEMENT, orderReadiness = ReviewRating.GOOD, handoffAccuracy = ReviewRating.NOT_OBSERVED),
        )
        val result = service(brandInputs = inputs).getBrandReport(10L)

        assertThat(result.status).isEqualTo(AggregationStatus.PUBLISHED)
        assertThat(result.contributorCount).isEqualTo(5)
        assertThat(result.metrics!!.packagingStability.distribution).containsExactlyEntriesOf(
            linkedMapOf(
                ReviewRating.VERY_GOOD to BigDecimal("40.0"),
                ReviewRating.GOOD to BigDecimal("20.0"),
                ReviewRating.NEEDS_IMPROVEMENT to BigDecimal("20.0"),
                ReviewRating.MAJOR_IMPROVEMENT to BigDecimal("20.0"),
            ),
        )
        assertThat(result.metrics.orderReadiness.distribution).containsEntry(ReviewRating.GOOD, BigDecimal("80.0"))
        assertThat(result.metrics.handoffAccuracy.observedCount).isZero()
        assertThat(result.metrics.handoffAccuracy.notObservedCount).isEqualTo(5)
        assertThat(result.metrics.handoffAccuracy.distribution).isEmpty()
    }

    @Test
    fun `pickup location metrics never use brand ratings`() {
        val inputs = (1L..5L).map { authorId ->
            input(
                reviewId = authorId,
                authorUserId = authorId,
                pickupSpaceCleanliness = ReviewRating.GOOD,
                staffInteraction = ReviewRating.NEEDS_IMPROVEMENT,
                riderRespect = ReviewRating.MAJOR_IMPROVEMENT,
                packagingStability = ReviewRating.VERY_GOOD,
                orderReadiness = ReviewRating.VERY_GOOD,
                handoffAccuracy = ReviewRating.VERY_GOOD,
            )
        }
        val result = service(locationInputs = inputs).getPickupLocationReport(20L)

        assertThat(result.status).isEqualTo(AggregationStatus.PUBLISHED)
        assertThat(result.metrics!!.pickupSpaceCleanliness.distribution.keys).containsExactly(ReviewRating.VERY_GOOD, ReviewRating.GOOD, ReviewRating.NEEDS_IMPROVEMENT, ReviewRating.MAJOR_IMPROVEMENT)
        assertThat(result.metrics.pickupSpaceCleanliness.distribution[ReviewRating.GOOD]).isEqualByComparingTo("100.0")
        assertThat(result.metrics.staffInteraction.distribution[ReviewRating.NEEDS_IMPROVEMENT]).isEqualByComparingTo("100.0")
        assertThat(result.metrics.riderRespect.distribution[ReviewRating.MAJOR_IMPROVEMENT]).isEqualByComparingTo("100.0")
    }

    @Test
    fun `one decimal distribution always totals one hundred`() {
        val inputs = listOf(
            input(1L, 1L, packagingStability = ReviewRating.VERY_GOOD),
            input(2L, 2L, packagingStability = ReviewRating.GOOD),
            input(3L, 3L, packagingStability = ReviewRating.NEEDS_IMPROVEMENT),
            input(4L, 4L, packagingStability = ReviewRating.MAJOR_IMPROVEMENT),
            input(5L, 5L, packagingStability = ReviewRating.MAJOR_IMPROVEMENT),
            input(6L, 6L, packagingStability = ReviewRating.MAJOR_IMPROVEMENT),
            input(7L, 7L, packagingStability = ReviewRating.NOT_OBSERVED),
            input(8L, 8L, packagingStability = ReviewRating.NOT_OBSERVED),
        )
        val metric = service(brandInputs = inputs).getBrandReport(10L).metrics!!.packagingStability

        assertThat(metric.observedCount).isEqualTo(6)
        assertThat(metric.notObservedCount).isEqualTo(2)
        assertThat(metric.distribution.values).containsExactly(
            BigDecimal("16.7"),
            BigDecimal("16.7"),
            BigDecimal("16.6"),
            BigDecimal("50.0"),
        )
        assertThat(metric.distribution.values.reduce(BigDecimal::add)).isEqualByComparingTo("100.0")
        assertThat(metric.distribution.values).allMatch { it.scale() == 1 }
    }

    private fun service(
        brandInputs: List<AggregateReviewInput> = emptyList(),
        locationInputs: List<AggregateReviewInput> = emptyList(),
    ) = ReviewAggregateService(FakeAggregateReviewQuery(brandInputs, locationInputs))

    private fun input(
        reviewId: Long,
        authorUserId: Long,
        createdAt: String = "2026-07-25T03:00:00Z",
        pickupSpaceCleanliness: ReviewRating = ReviewRating.GOOD,
        packagingStability: ReviewRating = ReviewRating.GOOD,
        orderReadiness: ReviewRating = ReviewRating.GOOD,
        handoffAccuracy: ReviewRating = ReviewRating.GOOD,
        staffInteraction: ReviewRating = ReviewRating.GOOD,
        riderRespect: ReviewRating = ReviewRating.GOOD,
    ) = AggregateReviewInput(
        reviewId = reviewId,
        authorUserId = authorUserId,
        ratings = ReviewRatings(
            pickupSpaceCleanliness = pickupSpaceCleanliness,
            packagingStability = packagingStability,
            orderReadiness = orderReadiness,
            handoffAccuracy = handoffAccuracy,
            staffInteraction = staffInteraction,
            riderRespect = riderRespect,
        ),
        createdAt = Instant.parse(createdAt),
    )

    private class FakeAggregateReviewQuery(
        private val brandInputs: List<AggregateReviewInput>,
        private val locationInputs: List<AggregateReviewInput>,
    ) : AggregateReviewQuery {
        override fun findCurrentActiveByRestaurantId(restaurantId: Long) = brandInputs

        override fun findLatestCurrentActiveByPickupLocationId(pickupLocationId: Long) = locationInputs
    }
}
