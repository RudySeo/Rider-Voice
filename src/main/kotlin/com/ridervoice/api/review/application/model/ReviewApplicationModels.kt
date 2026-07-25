package com.ridervoice.api.review.application.model

import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
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
