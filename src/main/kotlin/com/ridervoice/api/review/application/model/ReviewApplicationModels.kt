package com.ridervoice.api.review.application.model

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import java.math.BigDecimal
import java.time.Instant

data class ReviewCursor(
    val createdAt: Instant,
    val reviewId: Long,
) {
    init {
        require(reviewId > 0) { "Review cursor ID must be positive" }
    }
}

data class ReviewRestaurantSummary(
    val restaurantId: Long,
    val name: String,
    val address: String,
) {
    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
        require(address.isNotBlank()) { "Restaurant address must not be blank" }
    }
}

data class ReviewResult(
    val reviewId: Long,
    val restaurant: ReviewRestaurantSummary,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val commentModerationStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
    val historyStatus: ReviewHistoryStatus,
    val sequence: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(sequence > 0) { "Review sequence must be positive" }
        require(!updatedAt.isBefore(createdAt)) { "Review update time must not precede creation time" }
    }
}

data class MyReviewListResult(
    val items: List<ReviewResult>,
    val nextCursor: ReviewCursor?,
)

data class AggregateReviewInput(
    val reviewId: Long,
    val authorUserId: Long,
    val ratings: ReviewRatings,
    val createdAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(authorUserId > 0) { "Author user ID must be positive" }
    }
}

data class AggregateMetricResult(
    val observedCount: Int,
    val notObservedCount: Int,
    val distribution: Map<ReviewRating, BigDecimal>,
) {
    init {
        require(observedCount >= 0) { "Observed count must not be negative" }
        require(notObservedCount >= 0) { "Not observed count must not be negative" }
        require(ReviewRating.NOT_OBSERVED !in distribution) {
            "NOT_OBSERVED must not be included in the observed distribution"
        }
        if (observedCount == 0) {
            require(distribution.isEmpty()) {
                "A metric without observations must have an empty distribution"
            }
        } else {
            require(distribution.keys == OBSERVED_REVIEW_RATINGS) {
                "An observed distribution must include all observed rating values"
            }
            require(distribution.values.all { it.signum() >= 0 && it.scale() == 1 }) {
                "Distribution percentages must be non-negative with one decimal place"
            }
            require(distribution.values.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(FULL_PERCENTAGE) == 0) {
                "Distribution percentages must total 100.0"
            }
        }
    }

    private companion object {
        val OBSERVED_REVIEW_RATINGS = ReviewRating.entries
            .filterNot { it == ReviewRating.NOT_OBSERVED }
            .toSet()
        val FULL_PERCENTAGE = BigDecimal("100.0")
    }
}

data class BrandAggregateMetrics(
    val packagingStability: AggregateMetricResult,
    val orderReadiness: AggregateMetricResult,
    val handoffAccuracy: AggregateMetricResult,
)

data class PickupLocationAggregateMetrics(
    val pickupSpaceCleanliness: AggregateMetricResult,
    val staffInteraction: AggregateMetricResult,
    val riderRespect: AggregateMetricResult,
)

data class BrandAggregateResult(
    val status: AggregationStatus,
    val contributorCount: Int,
    val metrics: BrandAggregateMetrics?,
) {
    init {
        requireValidAggregateResult(status, contributorCount, metrics != null)
    }
}

data class PickupLocationAggregateResult(
    val status: AggregationStatus,
    val contributorCount: Int,
    val metrics: PickupLocationAggregateMetrics?,
) {
    init {
        requireValidAggregateResult(status, contributorCount, metrics != null)
    }
}

private fun requireValidAggregateResult(
    status: AggregationStatus,
    contributorCount: Int,
    hasMetrics: Boolean,
) {
    require(contributorCount >= 0) { "Contributor count must not be negative" }
    when (status) {
        AggregationStatus.NO_REVIEWS -> require(contributorCount == 0 && !hasMetrics) {
            "NO_REVIEWS requires zero contributors and no metrics"
        }
        AggregationStatus.COLLECTING -> require(contributorCount in 1..4 && !hasMetrics) {
            "COLLECTING requires one to four contributors and no metrics"
        }
        AggregationStatus.PUBLISHED -> require(contributorCount >= 5 && hasMetrics) {
            "PUBLISHED requires at least five contributors and metrics"
        }
    }
}
