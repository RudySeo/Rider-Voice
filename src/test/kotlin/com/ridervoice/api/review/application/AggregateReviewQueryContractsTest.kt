package com.ridervoice.api.review.application

import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AggregateReviewQueryContractsTest {

    @Test
    fun `aggregate query exposes batch brand summaries and detailed report lookups`() {
        assertThat(AggregateReviewQuery::class.java.isInterface).isTrue()
        assertThat(AggregateReviewQuery::class.java.declaredMethods.map { it.name })
            .containsExactlyInAnyOrder(
                "findCurrentActiveByRestaurantId",
                "findLatestCurrentActiveByPickupLocationId",
                "countDistinctCurrentActiveAuthorsByRestaurantIds",
            )
    }

    @Test
    fun `aggregate input exposes only review author ratings and creation time`() {
        val createdAt = Instant.parse("2026-07-25T03:00:00Z")
        val input = AggregateReviewInput(
            reviewId = 100L,
            authorUserId = 7L,
            ratings = ratings(),
            createdAt = createdAt,
        )

        assertThat(input.reviewId).isEqualTo(100L)
        assertThat(input.authorUserId).isEqualTo(7L)
        assertThat(input.ratings).isEqualTo(ratings())
        assertThat(input.createdAt).isEqualTo(createdAt)
        assertThat(AggregateReviewInput::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("reviewId", "authorUserId", "ratings", "createdAt")
        assertThat(AggregateReviewInput::class.java.declaredFields.map { it.type.name })
            .noneMatch { it.endsWith(".Review") || it.endsWith(".Restaurant") || it.endsWith(".PickupLocation") }
    }

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.NEEDS_IMPROVEMENT,
        handoffAccuracy = ReviewRating.MAJOR_IMPROVEMENT,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )
}
