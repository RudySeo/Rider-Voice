package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.AggregateReviewInput
import com.ridervoice.api.review.application.model.PublicAuthorActivityInput
import com.ridervoice.api.review.application.model.PublicReviewListItemInput
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.PublicReviewQuery
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.ReviewSubmissionSnapshot
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Component
internal class AggregateReviewQueryPersistenceAdapter(
    private val reviews: SpringDataReviewRepository,
) : AggregateReviewQuery {

    override fun findCurrentActiveByRestaurantId(restaurantId: Long): List<AggregateReviewInput> {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        return latestByAuthor(
            reviews.findCurrentAggregateRowsByRestaurantId(restaurantId, ReviewVisibilityStatus.ACTIVE),
        )
    }

    override fun findLatestCurrentActiveByPickupLocationId(
        pickupLocationId: Long,
    ): List<AggregateReviewInput> {
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
        return latestByAuthor(
            reviews.findCurrentAggregateRowsByPickupLocationId(
                pickupLocationId,
                ReviewVisibilityStatus.ACTIVE,
            ),
        )
    }

    private fun latestByAuthor(rows: List<AggregateReviewProjection>): List<AggregateReviewInput> =
        rows.groupBy(AggregateReviewProjection::authorUserId)
            .map { (_, authorRows) ->
                authorRows.maxWith(
                    compareBy<AggregateReviewProjection>(AggregateReviewProjection::createdAt)
                        .thenBy(AggregateReviewProjection::reviewId),
                )
            }
            .sortedBy(AggregateReviewProjection::authorUserId)
            .map { it.toInput() }

    private fun AggregateReviewProjection.toInput() = AggregateReviewInput(
        reviewId = reviewId,
        authorUserId = authorUserId,
        ratings = ReviewRatings(
            pickupSpaceCleanliness = pickupSpaceCleanliness,
            packagingStability = packagingStability,
            orderReadiness = orderReadiness,
            handoffAccuracy = handoffAccuracy,
            staffInteraction = staffInteraction,
            riderRespect = riderRespect,
        ),
        createdAt = createdAt,
    )
}

@Component
internal class PublicReviewQueryPersistenceAdapter(
    private val reviews: SpringDataReviewRepository,
) : PublicReviewQuery {

    override fun findActiveByRestaurantId(
        restaurantId: Long,
        cursor: ReviewCursor?,
        limit: Int,
    ): List<PublicReviewListItemInput> {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(limit > 0) { "Review query limit must be positive" }
        val pageable = PageRequest.of(0, limit)
        val rows = if (cursor == null) {
            reviews.findAllActiveByRestaurantId(restaurantId, ReviewVisibilityStatus.ACTIVE, pageable)
        } else {
            reviews.findAllActiveByRestaurantIdBeforeCursor(
                restaurantId,
                ReviewVisibilityStatus.ACTIVE,
                cursor.createdAt,
                cursor.reviewId,
                pageable,
            )
        }
        return rows.map { it.toInput() }
    }

    override fun findAuthorActivities(authorUserIds: Set<Long>): List<PublicAuthorActivityInput> {
        if (authorUserIds.isEmpty()) return emptyList()
        return reviews.findPublicAuthorActivities(authorUserIds, ReviewVisibilityStatus.ACTIVE)
            .map { row ->
                PublicAuthorActivityInput(
                    authorUserId = row.authorUserId,
                    firstPublicReviewAt = row.firstPublicReviewAt,
                    publicReviewCount = row.publicReviewCount,
                )
            }
    }

    private fun PublicReviewProjection.toInput() = PublicReviewListItemInput(
        reviewId = reviewId,
        authorUserId = authorUserId,
        visitMonth = visitMonth,
        ratings = ReviewRatings(
            pickupSpaceCleanliness = pickupSpaceCleanliness,
            packagingStability = packagingStability,
            orderReadiness = orderReadiness,
            handoffAccuracy = handoffAccuracy,
            staffInteraction = staffInteraction,
            riderRespect = riderRespect,
        ),
        comment = comment,
        commentModerationStatus = commentModerationStatus,
        createdAt = createdAt,
    )
}

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
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
        )
    }

    override fun save(review: Review): Review = reviews.saveAndFlush(review)

    override fun findLatestSubmissionForUpdate(
        authorUserId: Long,
        restaurantId: Long,
    ): ReviewSubmissionSnapshot? = reviews.findLatestSubmissionForUpdate(
        authorUserId,
        restaurantId,
        PageRequest.of(0, 1),
    ).firstOrNull()?.let { review ->
        ReviewSubmissionSnapshot(
            reviewId = review.id,
            authorUserId = review.author.id,
            restaurantId = review.restaurant.id,
            submittedAt = review.createdAt,
            active = review.isActive,
        )
    }

    override fun findOwnedActiveForUpdate(authorUserId: Long, reviewId: Long): Review? =
        reviews.findOwnedActiveForUpdate(authorUserId, reviewId, ReviewVisibilityStatus.ACTIVE).orElse(null)

    override fun findOwnedActive(authorUserId: Long, reviewId: Long): Review? =
        reviews.findOwnedActive(authorUserId, reviewId, ReviewVisibilityStatus.ACTIVE).orElse(null)

    override fun countAllByAuthorUserId(authorUserId: Long): Long = reviews.countByAuthorId(authorUserId)

    override fun countPubliclyVisibleByAuthorUserId(authorUserId: Long): Long =
        reviews.countPubliclyVisibleByAuthorId(authorUserId, ReviewVisibilityStatus.ACTIVE)

    override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long =
        reviews.countByAuthorIdAndCreatedAtGreaterThanEqual(authorUserId, since)

    override fun findByAuthorUserId(
        authorUserId: Long,
        cursor: ReviewCursor?,
        limit: Int,
    ): List<Review> {
        val pageable = PageRequest.of(0, limit)
        return if (cursor == null) {
            reviews.findAllByAuthorId(authorUserId, ReviewVisibilityStatus.ACTIVE, pageable)
        } else {
            reviews.findAllByAuthorIdBeforeCursor(
                authorUserId,
                ReviewVisibilityStatus.ACTIVE,
                cursor.createdAt,
                cursor.reviewId,
                pageable,
            )
        }
    }
}
