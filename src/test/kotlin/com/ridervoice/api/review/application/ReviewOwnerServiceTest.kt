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
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.ReviewSubmissionSnapshot
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReviewOwnerServiceTest {

    @Test
    fun `owner can update an active review without changing visit month`() {
        val review = review("기존 공개 의견").also { it.publishComment() }
        val fixture = fixture(review)

        val result = fixture.service.update(UpdateReviewCommand(AUTHOR_ID, REVIEW_ID, changedRatings(), "  새 의견  "))

        assertThat(result.visitMonth).isEqualTo(VisitMonth.parse("2026-07"))
        assertThat(result.ratings).isEqualTo(changedRatings())
        assertThat(result.comment).isEqualTo("새 의견")
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)
    }

    @Test
    fun `other authors and inactive reviews receive not found`() {
        val fixture = fixture(null)

        assertThatThrownBy {
            fixture.service.update(UpdateReviewCommand(OTHER_AUTHOR_ID, REVIEW_ID, changedRatings(), null))
        }.isInstanceOf(ResourceNotFoundException::class.java)
        assertThatThrownBy {
            fixture.service.delete(DeleteReviewCommand(AUTHOR_ID, REVIEW_ID))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `delete soft deletes the active review at the application clock`() {
        val review = review()
        val fixture = fixture(review)

        fixture.service.delete(DeleteReviewCommand(AUTHOR_ID, REVIEW_ID))

        assertThat(review.deletedAt).isEqualTo(NOW)
        assertThat(review.currentSlot).isNull()
        assertThat(fixture.reviews.saved).containsExactly(review)
    }

    @Test
    fun `my review list contains active reviews and returns a cursor`() {
        val first = review().also { it.id = 103L; setAuditTimes(it, NOW) }
        val second = review().also { it.id = 102L; setAuditTimes(it, NOW.minusSeconds(1)) }
        val extra = review().also { it.id = 101L; setAuditTimes(it, NOW.minusSeconds(2)) }
        val fixture = fixture(first, listOf(first, second, extra))

        val result = fixture.service.list(ListMyReviewsCommand(AUTHOR_ID, null, 2))

        assertThat(result.items.map { it.reviewId }).containsExactly(103L, 102L)
        assertThat(result.nextCursor).isEqualTo(ReviewCursor(second.createdAt, second.id))
    }

    private fun fixture(active: Review?, listed: List<Review> = emptyList()): Fixture {
        val repository = FakeReviewRepository(active, listed)
        return Fixture(
            ReviewOwnerService(repository, Clock.fixed(NOW, ZoneOffset.UTC)),
            repository,
        )
    }

    private fun review(comment: String? = null) = Review(
        User().also { it.id = AUTHOR_ID },
        Restaurant(
            "테스트 브랜드",
            PickupLocation("서울 강남구 테헤란로 1", null, BigDecimal("37.5"), BigDecimal("127.0"), PickupLocationSource.KAKAO)
                .also { it.id = 20L },
        ).also { it.id = RESTAURANT_ID },
        VisitMonth.parse("2026-07"),
        ratings(),
        comment,
    ).also { it.id = REVIEW_ID; setAuditTimes(it, NOW.minusSeconds(10)) }

    private data class Fixture(val service: ReviewOwnerService, val reviews: FakeReviewRepository)

    private class FakeReviewRepository(
        private val active: Review?,
        private val listed: List<Review>,
    ) : ReviewRepository {
        val saved = mutableListOf<Review>()
        override fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot = error("not used")
        override fun save(review: Review): Review = review.also(saved::add)
        override fun findLatestSubmissionForUpdate(authorUserId: Long, restaurantId: Long): ReviewSubmissionSnapshot? = null
        override fun findOwnedActiveForUpdate(authorUserId: Long, reviewId: Long): Review? =
            active?.takeIf { it.author.id == authorUserId && it.id == reviewId && it.isActive }
        override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long = error("not used")
        override fun findByAuthorUserId(authorUserId: Long, cursor: ReviewCursor?, limit: Int) = listed.take(limit)
    }

    private fun setAuditTimes(entity: BaseEntity, instant: Instant) {
        listOf("createdAt", "updatedAt").forEach { name ->
            BaseEntity::class.java.getDeclaredField(name).also { it.isAccessible = true; it.set(entity, instant) }
        }
    }

    private fun ratings() = ReviewRatings(
        ReviewRating.GOOD, ReviewRating.VERY_GOOD, ReviewRating.GOOD,
        ReviewRating.GOOD, ReviewRating.NOT_OBSERVED, ReviewRating.GOOD,
    )
    private fun changedRatings() = ReviewRatings(
        ReviewRating.VERY_GOOD, ReviewRating.GOOD, ReviewRating.NEEDS_IMPROVEMENT,
        ReviewRating.MAJOR_IMPROVEMENT, ReviewRating.GOOD, ReviewRating.NOT_OBSERVED,
    )

    private companion object {
        const val AUTHOR_ID = 7L
        const val OTHER_AUTHOR_ID = 8L
        const val RESTAURANT_ID = 10L
        const val REVIEW_ID = 102L
        val NOW: Instant = Instant.parse("2026-07-25T03:00:00Z")
    }
}
