package com.ridervoice.api.review.application.port.out

import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.domain.Review
import java.time.Instant

interface ReviewRepository {
    fun save(review: Review): Review

    fun findOwnedCurrentForUpdate(authorUserId: Long, reviewId: Long): Review?

    fun delete(review: Review)

    fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long

    fun findByAuthorUserId(
        authorUserId: Long,
        cursor: ReviewCursor?,
        limit: Int,
    ): List<Review>
}

interface AuthorRestaurantReviewStateRepository {
    fun findForUpdate(authorUserId: Long, restaurantId: Long): AuthorRestaurantReviewStateSnapshot?

    fun save(state: AuthorRestaurantReviewStateSnapshot): AuthorRestaurantReviewStateSnapshot
}

data class AuthorRestaurantReviewStateSnapshot(
    val stateId: Long?,
    val authorUserId: Long,
    val restaurantId: Long,
    val lastSubmittedAt: Instant,
    val lastSequence: Long,
    val currentReviewId: Long?,
) {
    init {
        require(stateId == null || stateId > 0) { "Review state ID must be positive" }
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(lastSequence > 0) { "Last review sequence must be positive" }
        require(currentReviewId == null || currentReviewId > 0) { "Current review ID must be positive" }
    }
}
