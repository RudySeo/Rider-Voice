package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

internal interface SpringDataReviewRepository : JpaRepository<Review, Long> {

    @Query(
        """
        select review from Review review
        where review.author.id = :authorUserId and review.id = :reviewId
          and review.currentSlot is not null and review.deletedAt is null
          and review.visibilityStatus = :visibilityStatus
        """,
    )
    fun findOwnedActive(
        @Param("authorUserId") authorUserId: Long,
        @Param("reviewId") reviewId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): Optional<Review>

    fun countByAuthorId(authorUserId: Long): Long

    @Query(
        """
        select count(review.id) from Review review
        where review.author.id = :authorUserId
          and review.currentSlot is not null and review.deletedAt is null
          and review.visibilityStatus = :visibilityStatus
        """,
    )
    fun countPubliclyVisibleByAuthorId(
        @Param("authorUserId") authorUserId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
          and review.id = :reviewId
          and review.currentSlot is not null
          and review.deletedAt is null
          and review.visibilityStatus = :visibilityStatus
        """,
    )
    fun findOwnedActiveForUpdate(
        @Param("authorUserId") authorUserId: Long,
        @Param("reviewId") reviewId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): Optional<Review>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
          and review.restaurant.id = :restaurantId
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findLatestSubmissionForUpdate(
        @Param("authorUserId") authorUserId: Long,
        @Param("restaurantId") restaurantId: Long,
        pageable: Pageable,
    ): List<Review>

    fun countByAuthorIdAndCreatedAtGreaterThanEqual(authorUserId: Long, since: Instant): Long

    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
          and review.currentSlot is not null
          and review.deletedAt is null
          and review.visibilityStatus = :visibilityStatus
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllByAuthorId(
        @Param("authorUserId") authorUserId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        pageable: Pageable,
    ): List<Review>

    @Query(
        """
        select review
        from Review review
        where review.author.id = :authorUserId
          and review.currentSlot is not null
          and review.deletedAt is null
          and review.visibilityStatus = :visibilityStatus
          and (
              review.createdAt < :cursorCreatedAt
              or (review.createdAt = :cursorCreatedAt and review.id < :cursorReviewId)
          )
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllByAuthorIdBeforeCursor(
        @Param("authorUserId") authorUserId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorReviewId") cursorReviewId: Long,
        pageable: Pageable,
    ): List<Review>

    @Query(
        """
        select review.id as reviewId,
               review.author.id as authorUserId,
               review.visitMonth as visitMonth,
               review.ratings.pickupSpaceCleanliness as pickupSpaceCleanliness,
               review.ratings.packagingStability as packagingStability,
               review.ratings.orderReadiness as orderReadiness,
               review.ratings.handoffAccuracy as handoffAccuracy,
               review.ratings.staffInteraction as staffInteraction,
               review.ratings.riderRespect as riderRespect,
               review.comment as comment,
               review.commentModerationStatus as commentModerationStatus,
               review.createdAt as createdAt
        from Review review
        where review.restaurant.id = :restaurantId
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllActiveByRestaurantId(
        @Param("restaurantId") restaurantId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        pageable: Pageable,
    ): List<PublicReviewProjection>

    @Query(
        """
        select review.id as reviewId,
               review.author.id as authorUserId,
               review.visitMonth as visitMonth,
               review.ratings.pickupSpaceCleanliness as pickupSpaceCleanliness,
               review.ratings.packagingStability as packagingStability,
               review.ratings.orderReadiness as orderReadiness,
               review.ratings.handoffAccuracy as handoffAccuracy,
               review.ratings.staffInteraction as staffInteraction,
               review.ratings.riderRespect as riderRespect,
               review.comment as comment,
               review.commentModerationStatus as commentModerationStatus,
               review.createdAt as createdAt
        from Review review
        where review.restaurant.id = :restaurantId
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
          and (
              review.createdAt < :cursorCreatedAt
              or (review.createdAt = :cursorCreatedAt and review.id < :cursorReviewId)
          )
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllActiveByRestaurantIdBeforeCursor(
        @Param("restaurantId") restaurantId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorReviewId") cursorReviewId: Long,
        pageable: Pageable,
    ): List<PublicReviewProjection>

    @Query(
        """
        select review.author.id as authorUserId,
               min(review.createdAt) as firstPublicReviewAt,
               count(review.id) as publicReviewCount
        from Review review
        where review.author.id in :authorUserIds
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
        group by review.author.id
        """,
    )
    fun findPublicAuthorActivities(
        @Param("authorUserIds") authorUserIds: Set<Long>,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): List<PublicAuthorActivityProjection>
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
        from Review review
        where review.restaurant.id = :restaurantId
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
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
        from Review review
        where review.restaurant.pickupLocation.id = :pickupLocationId
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
        """,
    )
    fun findCurrentAggregateRowsByPickupLocationId(
        @Param("pickupLocationId") pickupLocationId: Long,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): List<AggregateReviewProjection>

    @Query(
        """
        select review.restaurant.id as restaurantId,
               count(distinct review.author.id) as contributorCount
        from Review review
        where review.restaurant.id in :restaurantIds
          and review.visibilityStatus = :visibilityStatus
          and review.currentSlot is not null
          and review.deletedAt is null
        group by review.restaurant.id
        """,
    )
    fun countDistinctCurrentActiveAuthorsByRestaurantIds(
        @Param("restaurantIds") restaurantIds: Set<Long>,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
    ): List<RestaurantContributorCountProjection>

}

internal interface RestaurantContributorCountProjection {
    val restaurantId: Long
    val contributorCount: Long
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

internal interface PublicReviewProjection {
    val reviewId: Long
    val authorUserId: Long
    val visitMonth: VisitMonth
    val pickupSpaceCleanliness: ReviewRating
    val packagingStability: ReviewRating
    val orderReadiness: ReviewRating
    val handoffAccuracy: ReviewRating
    val staffInteraction: ReviewRating
    val riderRespect: ReviewRating
    val comment: String?
    val commentModerationStatus: ReviewCommentStatus
    val createdAt: Instant
}

internal interface PublicAuthorActivityProjection {
    val authorUserId: Long
    val firstPublicReviewAt: Instant
    val publicReviewCount: Long
}
