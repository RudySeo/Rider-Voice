package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateRepository
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateSnapshot
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.Review
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Component
internal class ReviewPersistenceAdapter(
    private val reviews: SpringDataReviewRepository,
    private val entityManager: EntityManager,
) : ReviewRepository {

    override fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot {
        val saved = reviews.saveAndFlush(
            Review(
                author = entityManager.getReference(User::class.java, command.authorUserId),
                restaurant = entityManager.getReference(Restaurant::class.java, command.restaurantId),
                visitMonth = command.visitMonth,
                ratings = command.ratings,
                comment = command.comment,
                sequence = command.sequence,
            ),
        )
        return SavedReviewSnapshot(
            reviewId = saved.id,
            restaurant = ReviewRestaurantSummary(
                restaurantId = saved.restaurant.id,
                name = saved.restaurant.brandName,
                address = saved.restaurant.pickupLocation.standardAddress,
            ),
            visitMonth = saved.visitMonth,
            ratings = saved.ratings,
            comment = saved.comment,
            commentModerationStatus = saved.commentModerationStatus,
            visibilityStatus = saved.visibilityStatus,
            sequence = saved.sequence,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
        )
    }

    override fun save(review: Review): Review = reviews.saveAndFlush(review)

    override fun findOwnedCurrentForUpdate(authorUserId: Long, reviewId: Long): Review? =
        reviews.findOwnedCurrentForUpdate(authorUserId, reviewId).orElse(null)

    override fun delete(review: Review) {
        reviews.delete(review)
    }

    override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long =
        reviews.countByAuthorIdAndCreatedAtGreaterThanEqual(authorUserId, since)

    override fun findByAuthorUserId(
        authorUserId: Long,
        cursor: ReviewCursor?,
        limit: Int,
    ): List<Review> {
        val pageable = PageRequest.of(0, limit)
        return if (cursor == null) {
            reviews.findAllByAuthorId(authorUserId, pageable)
        } else {
            reviews.findAllByAuthorIdBeforeCursor(
                authorUserId,
                cursor.createdAt,
                cursor.reviewId,
                pageable,
            )
        }
    }
}

@Component
internal class AuthorRestaurantReviewStatePersistenceAdapter(
    private val states: SpringDataAuthorRestaurantReviewStateRepository,
    private val entityManager: EntityManager,
) : AuthorRestaurantReviewStateRepository {

    override fun findForUpdate(
        authorUserId: Long,
        restaurantId: Long,
    ): AuthorRestaurantReviewStateSnapshot? = states.findForUpdate(authorUserId, restaurantId)
        .orElse(null)
        ?.toSnapshot()

    override fun findByAuthorUserIdAndRestaurantIds(
        authorUserId: Long,
        restaurantIds: Set<Long>,
    ): List<AuthorRestaurantReviewStateSnapshot> {
        if (restaurantIds.isEmpty()) return emptyList()
        return states.findAllByAuthorIdAndRestaurantIds(authorUserId, restaurantIds).map { it.toSnapshot() }
    }

    override fun save(state: AuthorRestaurantReviewStateSnapshot): AuthorRestaurantReviewStateSnapshot {
        val currentReview = state.currentReviewId?.let { entityManager.getReference(Review::class.java, it) }
        val entity = if (state.stateId == null) {
            AuthorRestaurantReviewState(
                author = entityManager.getReference(User::class.java, state.authorUserId),
                restaurant = entityManager.getReference(Restaurant::class.java, state.restaurantId),
                lastSubmittedAt = state.lastSubmittedAt,
                lastSequence = state.lastSequence,
                currentReview = currentReview,
            )
        } else {
            states.findById(state.stateId).orElseThrow {
                IllegalStateException("Review state ${state.stateId} does not exist")
            }.also { existing ->
                require(existing.author.id == state.authorUserId) { "Review state author cannot change" }
                require(existing.restaurant.id == state.restaurantId) { "Review state restaurant cannot change" }
                existing.synchronize(state.lastSubmittedAt, state.lastSequence, currentReview)
            }
        }

        return states.saveAndFlush(entity).toSnapshot()
    }

    private fun AuthorRestaurantReviewState.toSnapshot() = AuthorRestaurantReviewStateSnapshot(
        stateId = id,
        authorUserId = author.id,
        restaurantId = restaurant.id,
        lastSubmittedAt = lastSubmittedAt,
        lastSequence = lastSequence,
        currentReviewId = currentReview?.id,
    )
}
