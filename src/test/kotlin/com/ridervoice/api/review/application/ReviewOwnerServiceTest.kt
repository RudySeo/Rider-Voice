package com.ridervoice.api.review.application

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateRepository
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateSnapshot
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ReviewOwnerServiceTest {

    @Test
    fun `owner can update only the current review without changing visit month`() {
        val review = review(sequence = 2L, comment = "기존 공개 의견").also {
            it.id = CURRENT_REVIEW_ID
            it.publishComment()
            setAuditTimes(it, CREATED_AT)
        }
        val fixture = fixture(currentReview = review)

        val result = fixture.service.update(
            UpdateReviewCommand(
                authorUserId = AUTHOR_ID,
                reviewId = CURRENT_REVIEW_ID,
                ratings = changedRatings(),
                comment = "  새 의견  ",
            ),
        )

        assertThat(result.visitMonth).isEqualTo(VisitMonth.parse("2026-07"))
        assertThat(result.ratings).isEqualTo(changedRatings())
        assertThat(result.comment).isEqualTo("새 의견")
        assertThat(result.comment).isNotEqualTo("기존 공개 의견")
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)
        assertThat(result.historyStatus).isEqualTo(ReviewHistoryStatus.CURRENT)
        assertThat(fixture.reviews.saved).containsExactly(review)
    }

    @Test
    fun `update validates the complete replacement before mutating or saving`() {
        val originalRatings = ratings()
        val review = review(ratings = originalRatings, comment = null).also {
            it.id = CURRENT_REVIEW_ID
            setAuditTimes(it, CREATED_AT)
        }
        val fixture = fixture(currentReview = review)

        assertThatThrownBy {
            fixture.service.update(
                UpdateReviewCommand(
                    authorUserId = AUTHOR_ID,
                    reviewId = CURRENT_REVIEW_ID,
                    ratings = changedRatings(),
                    comment = "가".repeat(201),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(review.ratings).isEqualTo(originalRatings)
        assertThat(review.comment).isNull()
        assertThat(fixture.reviews.saved).isEmpty()
    }

    @Test
    fun `rating update keeps an unchanged published comment published`() {
        val review = review(comment = "공개 의견").also {
            it.id = CURRENT_REVIEW_ID
            it.publishComment()
            setAuditTimes(it, CREATED_AT)
        }
        val fixture = fixture(currentReview = review)

        val result = fixture.service.update(
            UpdateReviewCommand(AUTHOR_ID, CURRENT_REVIEW_ID, changedRatings(), "  공개 의견  "),
        )

        assertThat(result.ratings).isEqualTo(changedRatings())
        assertThat(result.comment).isEqualTo("공개 의견")
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
    }

    @Test
    fun `other authors and history receive the same not found result`() {
        val fixture = fixture(currentReview = null)

        assertThatThrownBy {
            fixture.service.update(
                UpdateReviewCommand(OTHER_AUTHOR_ID, CURRENT_REVIEW_ID, changedRatings(), null),
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)

        assertThatThrownBy {
            fixture.service.delete(DeleteReviewCommand(AUTHOR_ID, HISTORY_REVIEW_ID))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `hard delete clears only the current pointer and preserves cooldown state without fallback`() {
        val current = review(sequence = 2L).also {
            it.id = CURRENT_REVIEW_ID
            setAuditTimes(it, CREATED_AT)
        }
        val history = review(sequence = 1L).also {
            it.id = HISTORY_REVIEW_ID
            setAuditTimes(it, CREATED_AT.minusSeconds(1))
        }
        val fixture = fixture(currentReview = current, listedReviews = listOf(history))

        val result = fixture.service.delete(DeleteReviewCommand(AUTHOR_ID, CURRENT_REVIEW_ID))

        assertThat(result.reviewId).isEqualTo(CURRENT_REVIEW_ID)
        assertThat(fixture.reviews.deleted).containsExactly(current)
        assertThat(fixture.states.state).isEqualTo(
            state(currentReviewId = null),
        )
        assertThat(fixture.states.state.lastSubmittedAt).isEqualTo(LAST_SUBMITTED_AT)
        assertThat(fixture.states.state.lastSequence).isEqualTo(2L)
        assertThat(fixture.states.state.currentReviewId).isNull()
    }

    @Test
    fun `my review cursor list exposes current history and comment moderation status`() {
        val current = review(sequence = 2L, comment = "검수 중").also {
            it.id = CURRENT_REVIEW_ID
            setAuditTimes(it, CREATED_AT)
        }
        val history = review(sequence = 1L, comment = "공개 의견").also {
            it.id = HISTORY_REVIEW_ID
            it.publishComment()
            setAuditTimes(it, CREATED_AT.minusSeconds(10))
        }
        val fixture = fixture(
            currentReview = current,
            listedReviews = listOf(current, history),
        )

        val result = fixture.service.list(ListMyReviewsCommand(AUTHOR_ID, cursor = null, size = 2))

        assertThat(result.items.map { it.historyStatus })
            .containsExactly(ReviewHistoryStatus.CURRENT, ReviewHistoryStatus.HISTORY)
        assertThat(result.items.map { it.commentModerationStatus })
            .containsExactly(ReviewCommentStatus.PENDING, ReviewCommentStatus.PUBLISHED)
        assertThat(result.nextCursor).isNull()
        assertThat(fixture.reviews.requestedLimit).isEqualTo(3)
        assertThat(fixture.states.requestedRestaurantIds).containsExactly(RESTAURANT_ID)
    }

    @Test
    fun `my review list returns a created at and id cursor when another item exists`() {
        val first = review(sequence = 3L).also {
            it.id = 103L
            setAuditTimes(it, CREATED_AT)
        }
        val second = review(sequence = 2L).also {
            it.id = 102L
            setAuditTimes(it, CREATED_AT.minusSeconds(1))
        }
        val extra = review(sequence = 1L).also {
            it.id = 101L
            setAuditTimes(it, CREATED_AT.minusSeconds(2))
        }
        val fixture = fixture(currentReview = first, listedReviews = listOf(first, second, extra))

        val result = fixture.service.list(ListMyReviewsCommand(AUTHOR_ID, null, 2))

        assertThat(result.items.map { it.reviewId }).containsExactly(103L, 102L)
        assertThat(result.nextCursor).isEqualTo(ReviewCursor(second.createdAt, second.id))
    }

    private fun fixture(
        currentReview: Review?,
        listedReviews: List<Review> = emptyList(),
    ): Fixture {
        val reviews = FakeReviewRepository(currentReview, listedReviews)
        val states = FakeStateRepository(state(currentReview?.id))
        return Fixture(ReviewOwnerService(reviews, states), reviews, states)
    }

    private fun review(
        sequence: Long = 2L,
        ratings: ReviewRatings = ratings(),
        comment: String? = null,
    ) = Review(
        author = User().also { it.id = AUTHOR_ID },
        restaurant = restaurant(),
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ratings,
        comment = comment,
        sequence = sequence,
    )

    private fun restaurant() = Restaurant(
        "테스트 브랜드",
        PickupLocation(
            standardAddress = "서울 강남구 테헤란로 1",
            detailAddress = null,
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            source = PickupLocationSource.KAKAO,
        ).also { it.id = PICKUP_LOCATION_ID },
    ).also { it.id = RESTAURANT_ID }

    private fun state(currentReviewId: Long?) = AuthorRestaurantReviewStateSnapshot(
        stateId = STATE_ID,
        authorUserId = AUTHOR_ID,
        restaurantId = RESTAURANT_ID,
        lastSubmittedAt = LAST_SUBMITTED_AT,
        lastSequence = 2L,
        currentReviewId = currentReviewId,
    )

    private data class Fixture(
        val service: ReviewOwnerService,
        val reviews: FakeReviewRepository,
        val states: FakeStateRepository,
    )

    private class FakeReviewRepository(
        private val currentReview: Review?,
        private val listedReviews: List<Review>,
    ) : ReviewRepository {
        val saved = mutableListOf<Review>()
        val deleted = mutableListOf<Review>()
        var requestedLimit: Int? = null

        override fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot = error("not used")

        override fun save(review: Review): Review = review.also(saved::add)

        override fun findOwnedCurrentForUpdate(authorUserId: Long, reviewId: Long): Review? =
            currentReview?.takeIf { it.author.id == authorUserId && it.id == reviewId }

        override fun delete(review: Review) {
            deleted += review
        }

        override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long = error("not used")

        override fun findByAuthorUserId(
            authorUserId: Long,
            cursor: ReviewCursor?,
            limit: Int,
        ): List<Review> {
            requestedLimit = limit
            return listedReviews.take(limit)
        }
    }

    private class FakeStateRepository(
        var state: AuthorRestaurantReviewStateSnapshot,
    ) : AuthorRestaurantReviewStateRepository {
        var requestedRestaurantIds: Set<Long> = emptySet()

        override fun findForUpdate(
            authorUserId: Long,
            restaurantId: Long,
        ): AuthorRestaurantReviewStateSnapshot? = state.takeIf {
            it.authorUserId == authorUserId && it.restaurantId == restaurantId
        }

        override fun findByAuthorUserIdAndRestaurantIds(
            authorUserId: Long,
            restaurantIds: Set<Long>,
        ): List<AuthorRestaurantReviewStateSnapshot> {
            requestedRestaurantIds = restaurantIds
            return listOf(state).filter {
                it.authorUserId == authorUserId && it.restaurantId in restaurantIds
            }
        }

        override fun save(
            state: AuthorRestaurantReviewStateSnapshot,
        ): AuthorRestaurantReviewStateSnapshot = state.also { this.state = it }
    }

    private fun setAuditTimes(entity: BaseEntity, instant: Instant) {
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            BaseEntity::class.java.getDeclaredField(fieldName).also { field ->
                field.isAccessible = true
                field.set(entity, instant)
            }
        }
    }

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun changedRatings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.VERY_GOOD,
        packagingStability = ReviewRating.GOOD,
        orderReadiness = ReviewRating.NEEDS_IMPROVEMENT,
        handoffAccuracy = ReviewRating.MAJOR_IMPROVEMENT,
        staffInteraction = ReviewRating.GOOD,
        riderRespect = ReviewRating.NOT_OBSERVED,
    )

    private companion object {
        const val AUTHOR_ID = 7L
        const val OTHER_AUTHOR_ID = 8L
        const val RESTAURANT_ID = 10L
        const val PICKUP_LOCATION_ID = 20L
        const val STATE_ID = 30L
        const val CURRENT_REVIEW_ID = 102L
        const val HISTORY_REVIEW_ID = 101L
        val CREATED_AT: Instant = Instant.parse("2026-07-25T03:00:00Z")
        val LAST_SUBMITTED_AT: Instant = Instant.parse("2026-07-25T03:00:00Z")
    }
}
