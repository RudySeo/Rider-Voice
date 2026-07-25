package com.ridervoice.api.review.application

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.review.application.model.AggregateMetricResult
import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.model.BrandAggregateMetrics
import com.ridervoice.api.review.application.model.BrandAggregateResult
import com.ridervoice.api.review.application.model.PickupLocationAggregateMetrics
import com.ridervoice.api.review.application.model.PickupLocationAggregateResult
import com.ridervoice.api.review.application.port.`in`.ReviewAggregateUseCase
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
import com.ridervoice.api.review.domain.ReviewRating
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
internal class ReviewAggregateService(
    private val aggregateReviews: AggregateReviewQuery,
) : ReviewAggregateUseCase {

    override fun getBrandReport(restaurantId: Long): BrandAggregateResult {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        val inputs = aggregateReviews.findCurrentActiveByRestaurantId(restaurantId).latestByAuthor()
        val status = aggregationStatus(inputs.size)
        return BrandAggregateResult(
            status = status,
            contributorCount = inputs.size,
            metrics = if (status == AggregationStatus.PUBLISHED) {
                BrandAggregateMetrics(
                    packagingStability = metric(inputs) { it.ratings.packagingStability },
                    orderReadiness = metric(inputs) { it.ratings.orderReadiness },
                    handoffAccuracy = metric(inputs) { it.ratings.handoffAccuracy },
                )
            } else {
                null
            },
        )
    }

    override fun getPickupLocationReport(pickupLocationId: Long): PickupLocationAggregateResult {
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
        val inputs = aggregateReviews.findLatestCurrentActiveByPickupLocationId(pickupLocationId)
            .latestByAuthor()
        val status = aggregationStatus(inputs.size)
        return PickupLocationAggregateResult(
            status = status,
            contributorCount = inputs.size,
            metrics = if (status == AggregationStatus.PUBLISHED) {
                PickupLocationAggregateMetrics(
                    pickupSpaceCleanliness = metric(inputs) { it.ratings.pickupSpaceCleanliness },
                    staffInteraction = metric(inputs) { it.ratings.staffInteraction },
                    riderRespect = metric(inputs) { it.ratings.riderRespect },
                )
            } else {
                null
            },
        )
    }

    private fun aggregationStatus(contributorCount: Int): AggregationStatus = when (contributorCount) {
        0 -> AggregationStatus.NO_REVIEWS
        in 1 until MINIMUM_PUBLISHED_CONTRIBUTORS -> AggregationStatus.COLLECTING
        else -> AggregationStatus.PUBLISHED
    }

    private fun List<AggregateReviewInput>.latestByAuthor(): List<AggregateReviewInput> =
        groupBy(AggregateReviewInput::authorUserId)
            .values
            .map { authorInputs ->
                authorInputs.maxWith(
                    compareBy<AggregateReviewInput>(AggregateReviewInput::createdAt)
                        .thenBy(AggregateReviewInput::reviewId),
                )
            }

    private fun metric(
        inputs: List<AggregateReviewInput>,
        rating: (AggregateReviewInput) -> ReviewRating,
    ): AggregateMetricResult {
        val counts = inputs.map(rating).groupingBy { it }.eachCount()
        val notObservedCount = counts[ReviewRating.NOT_OBSERVED] ?: 0
        val observedCount = inputs.size - notObservedCount
        return AggregateMetricResult(
            observedCount = observedCount,
            notObservedCount = notObservedCount,
            distribution = distribution(counts, observedCount),
        )
    }

    private fun distribution(
        counts: Map<ReviewRating, Int>,
        observedCount: Int,
    ): Map<ReviewRating, BigDecimal> {
        if (observedCount == 0) return emptyMap()

        val shares = OBSERVED_RATINGS.map { rating ->
            val numerator = (counts[rating] ?: 0) * PERCENTAGE_TENTHS
            PercentageShare(
                rating = rating,
                tenths = numerator / observedCount,
                remainder = numerator % observedCount,
            )
        }
        var remainingTenths = PERCENTAGE_TENTHS - shares.sumOf(PercentageShare::tenths)
        val additions = shares.sortedWith(
            compareByDescending<PercentageShare>(PercentageShare::remainder)
                .thenBy { OBSERVED_RATINGS.indexOf(it.rating) },
        ).associate { share ->
            share.rating to if (remainingTenths-- > 0) 1 else 0
        }

        return linkedMapOf<ReviewRating, BigDecimal>().apply {
            shares.forEach { share ->
                put(
                    share.rating,
                    BigDecimal.valueOf((share.tenths + additions.getValue(share.rating)).toLong(), 1),
                )
            }
        }
    }

    private data class PercentageShare(
        val rating: ReviewRating,
        val tenths: Int,
        val remainder: Int,
    )

    private companion object {
        const val MINIMUM_PUBLISHED_CONTRIBUTORS = 5
        const val PERCENTAGE_TENTHS = 1_000
        val OBSERVED_RATINGS = listOf(
            ReviewRating.VERY_GOOD,
            ReviewRating.GOOD,
            ReviewRating.NEEDS_IMPROVEMENT,
            ReviewRating.MAJOR_IMPROVEMENT,
        )
    }
}
