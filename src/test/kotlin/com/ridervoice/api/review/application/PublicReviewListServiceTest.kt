package com.ridervoice.api.review.application

import com.ridervoice.api.restaurant.application.port.`in`.ResolvedRestaurantTargetResult
import com.ridervoice.api.restaurant.application.port.`in`.ResolveRestaurantTargetUseCase
import com.ridervoice.api.review.application.model.PublicAuthorActivityInput
import com.ridervoice.api.review.application.model.PublicReviewListItemInput
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsCommand
import com.ridervoice.api.review.application.port.out.PublicReviewQuery
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublicReviewListServiceTest {

    @Test
    fun `lists active current and history with published comments anonymous activity and cursor`() {
        val first = item(103L, 7L, "2026-07-25T03:00:00Z", 103L, ReviewCommentStatus.PENDING)
        val history = item(102L, 7L, "2026-07-24T03:00:00Z", 103L, ReviewCommentStatus.PUBLISHED)
        val extra = item(101L, 8L, "2026-07-23T03:00:00Z", null, ReviewCommentStatus.REJECTED)
        val query = FakePublicReviewQuery(listOf(first, history, extra))
        val service = PublicReviewListService(
            reviews = query,
            resolveRestaurant = ResolveRestaurantTargetUseCase { ResolvedRestaurantTargetResult(20L) },
            clock = Clock.fixed(Instant.parse("2026-07-26T03:00:00Z"), ZoneOffset.UTC),
        )

        val result = service.list(ListPublicRestaurantReviewsCommand(10L, null, 2))

        assertThat(query.restaurantId).isEqualTo(20L)
        assertThat(query.limit).isEqualTo(3)
        assertThat(query.activityAuthorIds).containsExactlyInAnyOrder(7L)
        assertThat(result.items.map { it.reviewId }).containsExactly(103L, 102L)
        assertThat(result.items.map { it.current }).containsExactly(true, false)
        assertThat(result.items.map { it.comment }).containsExactly(null, "공개 의견")
        assertThat(result.items.map { it.authorActivity.activityMonths }).containsOnly(3)
        assertThat(result.items.map { it.authorActivity.publicReviewCount }).containsOnly(8L)
        assertThat(result.items.map { it.verificationStatus }).containsOnly("UNVERIFIED")
        assertThat(result.items.map { it.verificationNotice })
            .containsOnly(PublicReviewListService.VERIFICATION_NOTICE)
        assertThat(result.nextCursor).isEqualTo(ReviewCursor(history.createdAt, history.reviewId))
        assertThat(result.items.first().authorActivity::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("activityMonths", "publicReviewCount")
    }

    @Test
    fun `exposes only published comments`() {
        val statuses = listOf(
            ReviewCommentStatus.NONE,
            ReviewCommentStatus.PENDING,
            ReviewCommentStatus.PUBLISHED,
            ReviewCommentStatus.REJECTED,
            ReviewCommentStatus.HIDDEN_REPORTED,
        )
        val query = FakePublicReviewQuery(
            statuses.mapIndexed { index, status ->
                item(
                    reviewId = 200L - index,
                    authorUserId = 7L,
                    createdAt = "2026-07-${25 - index}T03:00:00Z",
                    currentReviewId = 200L,
                    commentStatus = status,
                )
            },
        )
        val service = PublicReviewListService(
            reviews = query,
            resolveRestaurant = ResolveRestaurantTargetUseCase { ResolvedRestaurantTargetResult(10L) },
            clock = Clock.fixed(Instant.parse("2026-07-26T03:00:00Z"), ZoneOffset.UTC),
        )

        val result = service.list(ListPublicRestaurantReviewsCommand(10L, null, statuses.size))

        assertThat(result.items.map { it.comment })
            .containsExactly(null, null, "공개 의견", null, null)
    }

    private class FakePublicReviewQuery(
        private val items: List<PublicReviewListItemInput>,
    ) : PublicReviewQuery {
        var restaurantId: Long? = null
        var limit: Int? = null
        var activityAuthorIds: Set<Long> = emptySet()

        override fun findActiveByRestaurantId(
            restaurantId: Long,
            cursor: ReviewCursor?,
            limit: Int,
        ): List<PublicReviewListItemInput> {
            this.restaurantId = restaurantId
            this.limit = limit
            return items.take(limit)
        }

        override fun findAuthorActivities(authorUserIds: Set<Long>): List<PublicAuthorActivityInput> {
            activityAuthorIds = authorUserIds
            return authorUserIds.map {
                PublicAuthorActivityInput(
                    authorUserId = it,
                    firstPublicReviewAt = Instant.parse("2026-05-31T23:00:00Z"),
                    publicReviewCount = 8L,
                )
            }
        }
    }

    private fun item(
        reviewId: Long,
        authorUserId: Long,
        createdAt: String,
        currentReviewId: Long?,
        commentStatus: ReviewCommentStatus,
    ) = PublicReviewListItemInput(
        reviewId = reviewId,
        authorUserId = authorUserId,
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ReviewRatings(
            pickupSpaceCleanliness = ReviewRating.GOOD,
            packagingStability = ReviewRating.VERY_GOOD,
            orderReadiness = ReviewRating.GOOD,
            handoffAccuracy = ReviewRating.GOOD,
            staffInteraction = ReviewRating.NOT_OBSERVED,
            riderRespect = ReviewRating.GOOD,
        ),
        comment = "공개 의견",
        commentModerationStatus = commentStatus,
        currentReviewId = currentReviewId,
        createdAt = Instant.parse(createdAt),
    )
}
