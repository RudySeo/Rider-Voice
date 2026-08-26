package com.ridervoice.api.review.application.port.`in`

import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand
import com.ridervoice.api.review.application.model.BrandAggregateResult
import com.ridervoice.api.review.application.model.MyReviewListResult
import com.ridervoice.api.review.application.model.PickupLocationAggregateResult
import com.ridervoice.api.review.application.model.PublicReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth

interface ReviewAggregateUseCase {
    fun getBrandReport(restaurantId: Long): BrandAggregateResult

    fun getPickupLocationReport(pickupLocationId: Long): PickupLocationAggregateResult
}

fun interface CreateReviewUseCase {
    fun create(command: CreateReviewCommand): ReviewResult
}

data class CreateReviewCommand(
    val authorUserId: Long,
    val restaurantTarget: RestaurantTargetCommand,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
    }
}

fun interface UpdateReviewUseCase {
    fun update(command: UpdateReviewCommand): ReviewResult
}

data class UpdateReviewCommand(
    val authorUserId: Long,
    val reviewId: Long,
    val ratings: ReviewRatings,
    val comment: String?,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

fun interface DeleteReviewUseCase {
    fun delete(command: DeleteReviewCommand): DeleteReviewResult
}

data class DeleteReviewCommand(
    val authorUserId: Long,
    val reviewId: Long,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

data class DeleteReviewResult(
    val reviewId: Long,
) {
    init {
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

fun interface ListMyReviewsUseCase {
    fun list(command: ListMyReviewsCommand): MyReviewListResult
}

fun interface GetOwnedReviewUseCase {
    fun get(query: GetOwnedReviewQuery): ReviewResult
}

data class GetOwnedReviewQuery(val authorUserId: Long, val reviewId: Long) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

data class ListMyReviewsCommand(
    val authorUserId: Long,
    val cursor: ReviewCursor?,
    val size: Int,
) {
    init {
        require(authorUserId > 0) { "Author user ID must be positive" }
        require(size in 1..MAX_PAGE_SIZE) { "Review list size must be between 1 and 50" }
    }

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}

fun interface ListPublicRestaurantReviewsUseCase {
    fun list(command: ListPublicRestaurantReviewsCommand): PublicReviewListResult
}

data class ListPublicRestaurantReviewsCommand(
    val restaurantId: Long,
    val cursor: ReviewCursor?,
    val size: Int,
) {
    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(size in 1..MAX_PAGE_SIZE) { "Review list size must be between 1 and 50" }
    }

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
