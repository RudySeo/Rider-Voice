package com.ridervoice.api.review.application.model

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(!updatedAt.isBefore(createdAt)) { "Review update time must not precede creation time" }
    }
}

data class MyReviewListResult(
    val items: List<ReviewResult>,
    val nextCursor: ReviewCursor?,
    val authoredCount: Long = 0,
    val publiclyVisibleCount: Long = 0,
)

data class PublicReviewListItemInput(
    val reviewId: Long,
    val authorUserId: Long,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val commentModerationStatus: ReviewCommentStatus,
    val createdAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(authorUserId > 0) { "Author user ID must be positive" }
    }
}

data class PublicAuthorActivityInput(
    val authorUserId: Long,
    val firstPublicReviewAt: Instant,
    val publicReviewCount: Long,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(publicReviewCount > 0) { "Public review count must be positive" }
    }
}

data class PublicReviewAuthorActivityResult(
    val activityMonths: Int,
    val publicReviewCount: Long,
) {
    init {
        require(activityMonths > 0) { "Activity months must be positive" }
        require(publicReviewCount > 0) { "Public review count must be positive" }
    }
}

data class PublicReviewListItemResult(
    val reviewId: Long,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val authorActivity: PublicReviewAuthorActivityResult,
    val createdAt: Instant,
    val verificationStatus: String,
    val verificationNotice: String,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(verificationStatus == "UNVERIFIED") { "Public reviews must be unverified" }
        require(verificationNotice.isNotBlank()) { "Verification notice must not be blank" }
    }
}

data class PublicReviewListResult(
    val items: List<PublicReviewListItemResult>,
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
    val score: BigDecimal? = null,
) {
    init {
        require(observedCount >= 0) { "Observed count must not be negative" }
        require(notObservedCount >= 0) { "Not observed count must not be negative" }
        require(ReviewRating.NOT_OBSERVED !in distribution) {
            "NOT_OBSERVED must not be included in the observed distribution"
        }
        if (observedCount == 0) {
            require(score == null) { "A metric without observations must not have a score" }
            require(distribution.isEmpty()) {
                "A metric without observations must have an empty distribution"
            }
        } else {
            require(score != null && score.scale() == 1 && score >= BigDecimal("1.0") && score <= BigDecimal("5.0")) {
                "An observed metric score must be between 1.0 and 5.0 with one decimal place"
            }
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

data class BrandAggregateSummaryResult(
    val restaurantId: Long,
    val status: AggregationStatus,
    val contributorCount: Int,
) {
    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(contributorCount >= 0) { "Contributor count must not be negative" }
        when (status) {
            AggregationStatus.NO_REVIEWS -> require(contributorCount == 0) {
                "NO_REVIEWS requires zero contributors"
            }
            AggregationStatus.COLLECTING -> require(contributorCount in 1..4) {
                "COLLECTING requires one to four contributors"
            }
            AggregationStatus.PUBLISHED -> require(contributorCount >= 5) {
                "PUBLISHED requires at least five contributors"
            }
        }
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
