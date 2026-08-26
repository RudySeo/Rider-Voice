package com.ridervoice.api.review.application.port.out

import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.model.PublicAuthorActivityInput
import com.ridervoice.api.review.application.model.PublicReviewListItemInput
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import java.time.Instant

interface AggregateReviewQuery {
    fun findCurrentActiveByRestaurantId(restaurantId: Long): List<AggregateReviewInput>

    fun findLatestCurrentActiveByPickupLocationId(pickupLocationId: Long): List<AggregateReviewInput>
}

interface PublicReviewQuery {
    fun findActiveByRestaurantId(
        restaurantId: Long,
        cursor: ReviewCursor?,
        limit: Int,
    ): List<PublicReviewListItemInput>

    fun findAuthorActivities(authorUserIds: Set<Long>): List<PublicAuthorActivityInput>
}

interface ReviewRepository {
    fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot

    fun save(review: Review): Review

    fun findLatestSubmissionForUpdate(authorUserId: Long, restaurantId: Long): ReviewSubmissionSnapshot?

    fun findOwnedActiveForUpdate(authorUserId: Long, reviewId: Long): Review?

    fun findOwnedActive(authorUserId: Long, reviewId: Long): Review?

    fun countAllByAuthorUserId(authorUserId: Long): Long

    fun countPubliclyVisibleByAuthorUserId(authorUserId: Long): Long

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
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(!updatedAt.isBefore(createdAt)) { "Review update time must not precede creation time" }
    }
}

data class ReviewSubmissionSnapshot(
    val reviewId: Long,
    val authorUserId: Long,
    val restaurantId: Long,
    val submittedAt: Instant,
    val active: Boolean,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}
