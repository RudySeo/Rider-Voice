package com.ridervoice.api.review.application.port.out

import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import java.time.Instant

interface ReviewRepository {
    fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot

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

data class NewReviewPersistenceCommand(
    val authorUserId: Long,
    val restaurantId: Long,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val sequence: Long,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(sequence > 0) { "Review sequence must be positive" }
    }
}

data class SavedReviewSnapshot(
    val reviewId: Long,
    val restaurant: ReviewRestaurantSummary,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val commentModerationStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
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

interface AuthorRestaurantReviewStateRepository {
    fun findForUpdate(authorUserId: Long, restaurantId: Long): AuthorRestaurantReviewStateSnapshot?

    fun findByAuthorUserIdAndRestaurantIds(
        authorUserId: Long,
        restaurantIds: Set<Long>,
    ): List<AuthorRestaurantReviewStateSnapshot>

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
