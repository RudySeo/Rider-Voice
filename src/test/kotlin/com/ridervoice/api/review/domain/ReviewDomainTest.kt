package com.ridervoice.api.review.domain

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ReviewDomainTest {

    @Test
    fun `review rating exposes exactly the five supported values`() {
        assertThat(ReviewRating.entries).containsExactly(
            ReviewRating.VERY_GOOD,
            ReviewRating.GOOD,
            ReviewRating.NEEDS_IMPROVEMENT,
            ReviewRating.MAJOR_IMPROVEMENT,
            ReviewRating.NOT_OBSERVED,
        )
    }

    @Test
    fun `review ratings require all six independent observations`() {
        val ratings = ratings()

        assertThat(ratings.pickupSpaceCleanliness).isEqualTo(ReviewRating.GOOD)
        assertThat(ratings.packagingStability).isEqualTo(ReviewRating.VERY_GOOD)
        assertThat(ratings.orderReadiness).isEqualTo(ReviewRating.NEEDS_IMPROVEMENT)
        assertThat(ratings.handoffAccuracy).isEqualTo(ReviewRating.MAJOR_IMPROVEMENT)
        assertThat(ratings.staffInteraction).isEqualTo(ReviewRating.NOT_OBSERVED)
        assertThat(ratings.riderRespect).isEqualTo(ReviewRating.GOOD)
    }

    @Test
    fun `comment is trimmed and starts pending while blank comment becomes none`() {
        val pending = review(comment = "  픽업 동선이 잘 구분되어 있었습니다.  ")
        val absent = review(comment = "  \n\t ")

        assertThat(pending.comment).isEqualTo("픽업 동선이 잘 구분되어 있었습니다.")
        assertThat(pending.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)
        assertThat(absent.comment).isNull()
        assertThat(absent.commentModerationStatus).isEqualTo(ReviewCommentStatus.NONE)
    }

    @Test
    fun `comment accepts two hundred characters and rejects longer normalized input`() {
        assertThat(review(comment = "가".repeat(200)).comment).hasSize(200)

        assertThatIllegalArgumentException().isThrownBy {
            review(comment = "  ${"가".repeat(201)}  ")
        }
    }

    @Test
    fun `comment moderation follows pending published reported and rejected transitions`() {
        val published = review(comment = "의견")
        published.publishComment()
        assertThat(published.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)

        published.hidePublishedCommentForReport()
        assertThat(published.commentModerationStatus).isEqualTo(ReviewCommentStatus.HIDDEN_REPORTED)

        published.restoreReportedComment()
        assertThat(published.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)

        val rejected = review(comment = "다른 의견")
        rejected.rejectComment()
        assertThat(rejected.commentModerationStatus).isEqualTo(ReviewCommentStatus.REJECTED)

        assertThatIllegalStateException().isThrownBy { rejected.publishComment() }
    }

    @Test
    fun `changing a published comment requires moderation again`() {
        val review = review(comment = "기존 공개 의견")
        review.publishComment()

        review.update(ratings(), "  수정 의견  ")

        assertThat(review.comment).isEqualTo("수정 의견")
        assertThat(review.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)

        review.update(ratings(), "  ")
        assertThat(review.comment).isNull()
        assertThat(review.commentModerationStatus).isEqualTo(ReviewCommentStatus.NONE)
    }

    @Test
    fun `review starts active and exclusion clears the current slot`() {
        val review = review()

        assertThat(review.isActive).isTrue()
        assertThat(review.currentSlot).isEqualTo(1)
        review.exclude()
        assertThat(review.visibilityStatus).isEqualTo(ReviewVisibilityStatus.EXCLUDED)
        assertThat(review.currentSlot).isNull()
        assertThat(review.isActive).isFalse()
        assertThatIllegalStateException().isThrownBy { review.exclude() }
    }

    @Test
    fun `soft delete preserves the review and clears its current slot`() {
        val review = review()
        val deletedAt = Instant.parse("2026-07-30T00:00:00Z")

        review.softDelete(deletedAt)

        assertThat(review.deletedAt).isEqualTo(deletedAt)
        assertThat(review.currentSlot).isNull()
        assertThat(review.isActive).isFalse()
        assertThatIllegalStateException().isThrownBy { review.softDelete(deletedAt) }
    }

    private fun review(comment: String? = null) = Review(
        author = User(),
        restaurant = Restaurant("테스트 브랜드", pickupLocation()),
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ratings(),
        comment = comment,
    )

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.NEEDS_IMPROVEMENT,
        handoffAccuracy = ReviewRating.MAJOR_IMPROVEMENT,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun pickupLocation() = PickupLocation(
        standardAddress = "서울 강남구 테헤란로 1",
        detailAddress = null,
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        source = PickupLocationSource.KAKAO,
    )
}
