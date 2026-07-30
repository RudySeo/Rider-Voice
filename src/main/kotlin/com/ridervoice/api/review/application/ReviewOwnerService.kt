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
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.Review
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
internal class ReviewOwnerService(
    private val reviews: ReviewRepository,
    private val clock: Clock,
) : UpdateReviewUseCase, DeleteReviewUseCase, ListMyReviewsUseCase {

    @Transactional
    override fun update(command: UpdateReviewCommand): ReviewResult {
        val review = reviews.findOwnedActiveForUpdate(command.authorUserId, command.reviewId)
            ?: throw reviewNotFound()

        review.update(command.ratings, command.comment)
        return reviews.save(review).toResult()
    }

    @Transactional
    override fun delete(command: DeleteReviewCommand): DeleteReviewResult {
        val review = reviews.findOwnedActiveForUpdate(command.authorUserId, command.reviewId)
            ?: throw reviewNotFound()

        review.softDelete(clock.instant())
        reviews.save(review)
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
        return MyReviewListResult(
            items = visibleItems.map { it.toResult() },
            nextCursor = if (page.size > command.size) {
                visibleItems.last().let { ReviewCursor(it.createdAt, it.id) }
            } else {
                null
            },
        )
    }

    private fun Review.toResult(): ReviewResult = ReviewResult(
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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun reviewNotFound() = ResourceNotFoundException("Review was not found")
}
