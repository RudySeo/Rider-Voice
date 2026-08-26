package com.ridervoice.api.review.application

import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.review.application.model.MyReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.CreateReviewUseCase
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewResult
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.ReviewSubmissionSnapshot
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ReviewApplicationContractsTest {

    @Test
    fun `create command carries an application target visit month six ratings and nullable comment`() {
        val targets = listOf(
            ExistingRestaurantTargetCommand(10L),
            KakaoRestaurantTargetCommand("강남 분식", "kakao-10"),
            ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = 20L,
                name = "새 배달 브랜드",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
            ManualAddressRestaurantTargetCommand(
                addressQuery = "서울 강남구 테헤란로 1",
                selectedStandardAddress = "서울 강남구 테헤란로 1",
                detailAddress = "지하 1층 픽업대",
                name = "새 배달 브랜드",
                platforms = setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )

        targets.forEach { target ->
            val command = CreateReviewCommand(
                authorUserId = 7L,
                restaurantTarget = target,
                visitMonth = VisitMonth.parse("2026-07"),
                ratings = ratings(),
                comment = null,
            )

            assertThat(command.restaurantTarget).isSameAs(target)
            assertThat(command.visitMonth).isEqualTo(VisitMonth.parse("2026-07"))
            assertThat(command.ratings).isEqualTo(ratings())
            assertThat(command.comment).isNull()
        }
        assertThat(RestaurantTargetCommand::class.isSealed).isTrue()
    }

    @Test
    fun `owner use cases expose entity free commands and results`() {
        val now = Instant.parse("2026-07-25T03:00:00Z")
        val result = reviewResult(now)
        val create = CreateReviewUseCase { result }
        val update = UpdateReviewUseCase { result }
        val delete = DeleteReviewUseCase { DeleteReviewResult(it.reviewId) }
        val list = ListMyReviewsUseCase {
            MyReviewListResult(
                items = listOf(result),
                nextCursor = ReviewCursor(now, 100L),
            )
        }

        assertThat(
            create.create(
                CreateReviewCommand(
                    authorUserId = 7L,
                    restaurantTarget = ExistingRestaurantTargetCommand(10L),
                    visitMonth = VisitMonth.parse("2026-07"),
                    ratings = ratings(),
                    comment = "의견",
                ),
            ),
        ).isEqualTo(result)
        assertThat(
            update.update(UpdateReviewCommand(7L, 100L, ratings(), null)),
        ).isEqualTo(result)
        assertThat(delete.delete(DeleteReviewCommand(7L, 100L)))
            .isEqualTo(DeleteReviewResult(100L))
        assertThat(list.list(ListMyReviewsCommand(7L, null, 20)).items)
            .containsExactly(result)

        assertThat(UpdateReviewCommand::class.java.declaredFields.map { it.name })
            .doesNotContain("visitMonth")
        assertThat(ReviewResult::class.java.declaredFields.map { it.type.name })
            .noneMatch { it.endsWith(".Review") || it.endsWith(".Restaurant") }
    }

    @Test
    fun `cursor and persistence dependencies expose application contracts`() {
        val cursor = ReviewCursor(Instant.parse("2026-07-25T03:00:00Z"), 100L)
        val submission = ReviewSubmissionSnapshot(
            reviewId = cursor.reviewId,
            authorUserId = 7L,
            restaurantId = 10L,
            submittedAt = cursor.createdAt,
            active = true,
        )

        assertThat(cursor.createdAt).isEqualTo(submission.submittedAt)
        assertThat(cursor.reviewId).isEqualTo(submission.reviewId)
        assertThat(ReviewRepository::class.java.isInterface).isTrue()
        assertThat(ReviewRepository::class.java.declaredMethods.map { it.name }).containsExactlyInAnyOrder(
            "create",
            "save",
            "findLatestSubmissionForUpdate",
            "findOwnedActiveForUpdate",
            "findOwnedActive",
            "countByAuthorUserIdSince",
            "countAllByAuthorUserId",
            "countPubliclyVisibleByAuthorUserId",
            "findByAuthorUserId",
        )
    }

    private fun reviewResult(now: Instant) = ReviewResult(
        reviewId = 100L,
        restaurant = ReviewRestaurantSummary(
            restaurantId = 10L,
            name = "라이더보이스 강남점",
            address = "서울 강남구 테헤란로 1",
        ),
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ratings(),
        comment = "의견",
        commentModerationStatus = ReviewCommentStatus.PENDING,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.NEEDS_IMPROVEMENT,
        handoffAccuracy = ReviewRating.MAJOR_IMPROVEMENT,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )
}
