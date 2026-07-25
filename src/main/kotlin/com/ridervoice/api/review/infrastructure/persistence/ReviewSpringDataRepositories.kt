package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

internal interface SpringDataReviewRepository : JpaRepository<Review, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select review
        from AuthorRestaurantReviewState state
        join state.currentReview review
        where state.author.id = :authorUserId
          and review.author.id = :authorUserId
          and review.id = :reviewId
        """,
    )
    fun findOwnedCurrentForUpdate(
        @Param("authorUserId") authorUserId: Long,
        @Param("reviewId") reviewId: Long,
    ): Optional<Review>

    fun countByAuthorIdAndCreatedAtGreaterThanEqual(authorUserId: Long, since: Instant): Long

    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllByAuthorId(
        @Param("authorUserId") authorUserId: Long,
        pageable: Pageable,
    ): List<Review>

    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
          and (
              review.createdAt < :cursorCreatedAt
              or (review.createdAt = :cursorCreatedAt and review.id < :cursorReviewId)
          )
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllByAuthorIdBeforeCursor(
        @Param("authorUserId") authorUserId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorReviewId") cursorReviewId: Long,
        pageable: Pageable,
    ): List<Review>
}

internal interface SpringDataAuthorRestaurantReviewStateRepository :
    JpaRepository<AuthorRestaurantReviewState, Long> {

    @Query(
        """
        select review.id as reviewId,
               review.author.id as authorUserId,
               review.createdAt as createdAt,
               review.ratings.pickupSpaceCleanliness as pickupSpaceCleanliness,
               review.ratings.packagingStability as packagingStability,
               review.ratings.orderReadiness as orderReadiness,
               review.ratings.handoffAccuracy as handoffAccuracy,
               review.ratings.staffInteraction as staffInteraction,
               review.ratings.riderRespect as riderRespect
        from AuthorRestaurantReviewState state
        join state.currentReview review
        where state.restaurant.id = :restaurantId
          and review.restaurant.id = :restaurantId
          and state.author.id = review.author.id
          and review.visibilityStatus = :visibilityStatus
        """,
    )
    fun findCurrentAggregateRowsByRestaurantId(
        @Param("restaurantId") restaurantId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): List<AggregateReviewProjection>

    @Query(
        """
        select review.id as reviewId,
               review.author.id as authorUserId,
               review.createdAt as createdAt,
               review.ratings.pickupSpaceCleanliness as pickupSpaceCleanliness,
               review.ratings.packagingStability as packagingStability,
               review.ratings.orderReadiness as orderReadiness,
               review.ratings.handoffAccuracy as handoffAccuracy,
               review.ratings.staffInteraction as staffInteraction,
               review.ratings.riderRespect as riderRespect
        from AuthorRestaurantReviewState state
        join state.currentReview review
        where state.restaurant.pickupLocation.id = :pickupLocationId
          and review.restaurant.pickupLocation.id = :pickupLocationId
          and state.author.id = review.author.id
          and review.visibilityStatus = :visibilityStatus
        """,
    )
    fun findCurrentAggregateRowsByPickupLocationId(
        @Param("pickupLocationId") pickupLocationId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): List<AggregateReviewProjection>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select state
        from AuthorRestaurantReviewState state
        where state.author.id = :authorUserId
          and state.restaurant.id = :restaurantId
        """,
    )
    fun findForUpdate(
        @Param("authorUserId") authorUserId: Long,
        @Param("restaurantId") restaurantId: Long,
    ): Optional<AuthorRestaurantReviewState>

    @Query(
        """
        select state
        from AuthorRestaurantReviewState state
        where state.author.id = :authorUserId
          and state.restaurant.id in :restaurantIds
        """,
    )
    fun findAllByAuthorIdAndRestaurantIds(
        @Param("authorUserId") authorUserId: Long,
        @Param("restaurantIds") restaurantIds: Set<Long>,
    ): List<AuthorRestaurantReviewState>
}

internal interface AggregateReviewProjection {
    val reviewId: Long
    val authorUserId: Long
    val createdAt: Instant
    val pickupSpaceCleanliness: ReviewRating
    val packagingStability: ReviewRating
    val orderReadiness: ReviewRating
    val handoffAccuracy: ReviewRating
    val staffInteraction: ReviewRating
    val riderRespect: ReviewRating
}
