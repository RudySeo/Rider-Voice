package com.ridervoice.api.review.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.review.application.model.MyReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewResult
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateRepository
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewHistoryPolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class ReviewOwnerService(
    private val reviews: ReviewRepository,
    private val states: AuthorRestaurantReviewStateRepository,
) : UpdateReviewUseCase, DeleteReviewUseCase, ListMyReviewsUseCase {

    @Transactional
    override fun update(command: UpdateReviewCommand): ReviewResult {
        val review = reviews.findOwnedCurrentForUpdate(command.authorUserId, command.reviewId)
            ?: throw reviewNotFound()

        review.update(command.ratings, command.comment)
        return reviews.save(review).toResult(currentReviewId = review.id)
    }

    @Transactional
    override fun delete(command: DeleteReviewCommand): DeleteReviewResult {
        val review = reviews.findOwnedCurrentForUpdate(command.authorUserId, command.reviewId)
            ?: throw reviewNotFound()
        val state = states.findForUpdate(command.authorUserId, review.restaurant.id)
            ?.takeIf { it.currentReviewId == review.id }
            ?: throw reviewNotFound()

        states.save(state.copy(currentReviewId = null))
        reviews.delete(review)
        return DeleteReviewResult(review.id)
    }

    @Transactional(readOnly = true)
    override fun list(command: ListMyReviewsCommand): MyReviewListResult {
        val page = reviews.findByAuthorUserId(
            authorUserId = command.authorUserId,
            cursor = command.cursor,
            limit = command.size + 1,
        )
        val visibleItems = page.take(command.size)
        val currentReviewIdsByRestaurant = states.findByAuthorUserIdAndRestaurantIds(
            authorUserId = command.authorUserId,
            restaurantIds = visibleItems.mapTo(linkedSetOf()) { it.restaurant.id },
        ).associate { it.restaurantId to it.currentReviewId }

        return MyReviewListResult(
            items = visibleItems.map { review ->
                review.toResult(currentReviewIdsByRestaurant[review.restaurant.id])
            },
            nextCursor = if (page.size > command.size) {
                visibleItems.last().let { ReviewCursor(it.createdAt, it.id) }
            } else {
                null
            },
        )
    }

    private fun Review.toResult(currentReviewId: Long?): ReviewResult = ReviewResult(
        reviewId = id,
        restaurant = ReviewRestaurantSummary(
            restaurantId = restaurant.id,
            name = restaurant.brandName,
            address = restaurant.pickupLocation.standardAddress,
        ),
        visitMonth = visitMonth,
        ratings = ratings,
        comment = comment,
        commentModerationStatus = commentModerationStatus,
        visibilityStatus = visibilityStatus,
        historyStatus = ReviewHistoryPolicy.classify(id, currentReviewId),
        sequence = sequence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun reviewNotFound() = ResourceNotFoundException("Review was not found")
}
