package com.ridervoice.api.review.application

import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ResolveValidatedRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ResolvedRestaurantTargetResult
import com.ridervoice.api.restaurant.application.port.`in`.ValidateRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ValidatedExistingRestaurantTarget
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.ReviewSubmissionSnapshot
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ReviewCreateServiceTest {

    @Test
    fun `validates the target before atomically resolving and saving an active review`() {
        val fixture = fixture()

        val result = fixture.service.create(command("  검수할 의견  "))

        assertThat(fixture.events).startsWith("validate", "transaction.begin", "resolve", "review.save")
        assertThat(result.comment).isEqualTo("검수할 의견")
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)
        assertThat(fixture.transactions.commits).isEqualTo(1)
    }

    @Test
    fun `active review blocks another submission even after ninety days`() {
        val fixture = fixture(
            latest = submission(NOW.minus(Duration.ofDays(180)), active = true),
        )

        assertThatThrownBy { fixture.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("active review")
        assertThat(fixture.reviews.created).isZero()
    }

    @Test
    fun `inactive review permits resubmission exactly at the ninety day boundary`() {
        val submittedAt = NOW.minus(Duration.ofDays(90))
        val before = fixture(now = NOW.minusNanos(1), latest = submission(submittedAt, active = false))
        assertThatThrownBy { before.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)

        val boundary = fixture(latest = submission(submittedAt, active = false))
        assertThat(boundary.service.create(command()).reviewId).isEqualTo(100L)
    }

    @Test
    fun `soft deleted submissions still count toward the recent twenty four hour limit`() {
        val fixture = fixture(recentReviewCount = 10L)

        assertThatThrownBy { fixture.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("24 hours")
        assertThat(fixture.reviews.countedSince).isEqualTo(NOW.minus(Duration.ofHours(24)))
        assertThat(fixture.events).doesNotContain("resolve", "review.save")
    }

    @Test
    fun `retries a concurrent unique race and reports the winning active review`() {
        val fixture = fixture(
            createFailure = DataIntegrityViolationException("concurrent winner"),
            winnerAfterFailure = submission(NOW, active = true),
        )

        assertThatThrownBy { fixture.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("active review")
        assertThat(fixture.events.count { it == "validate" }).isEqualTo(1)
        assertThat(fixture.events.count { it == "transaction.begin" }).isEqualTo(2)
    }

    private fun fixture(
        now: Instant = NOW,
        latest: ReviewSubmissionSnapshot? = null,
        recentReviewCount: Long = 0,
        createFailure: RuntimeException? = null,
        winnerAfterFailure: ReviewSubmissionSnapshot? = null,
    ): Fixture {
        val events = mutableListOf<String>()
        val reviews = FakeReviewRepository(events, now, latest, recentReviewCount, createFailure, winnerAfterFailure)
        val transactions = RecordingTransactionManager(events)
        val service = ReviewCreateService(
            targetValidator = ValidateRestaurantTargetUseCase {
                events += "validate"
                ValidatedExistingRestaurantTarget(RESTAURANT_ID)
            },
            targetResolver = ResolveValidatedRestaurantTargetUseCase {
                events += "resolve"
                ResolvedRestaurantTargetResult(RESTAURANT_ID)
            },
            reviews = reviews,
            transactionManager = transactions,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Fixture(service, events, reviews, transactions)
    }

    private fun command(comment: String? = null) = CreateReviewCommand(
        AUTHOR_ID,
        ExistingRestaurantTargetCommand(RESTAURANT_ID),
        VisitMonth.parse("2026-07"),
        ratings(),
        comment,
    )

    private fun submission(submittedAt: Instant, active: Boolean) = ReviewSubmissionSnapshot(
        90L, AUTHOR_ID, RESTAURANT_ID, submittedAt, active,
    )

    private data class Fixture(
        val service: ReviewCreateService,
        val events: MutableList<String>,
        val reviews: FakeReviewRepository,
        val transactions: RecordingTransactionManager,
    )

    private class FakeReviewRepository(
        private val events: MutableList<String>,
        private val now: Instant,
        initialLatest: ReviewSubmissionSnapshot?,
        private val recentReviewCount: Long,
        private var createFailure: RuntimeException?,
        private val winnerAfterFailure: ReviewSubmissionSnapshot?,
    ) : ReviewRepository {
        var latest = initialLatest
        var created = 0
        var countedSince: Instant? = null

        override fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot {
            events += "review.save"
            createFailure?.let {
                createFailure = null
                latest = winnerAfterFailure
                throw it
            }
            created++
            return SavedReviewSnapshot(
                100L,
                ReviewRestaurantSummary(command.restaurantId, "테스트 브랜드", "서울 강남구 테헤란로 1"),
                command.visitMonth,
                command.ratings,
                command.comment?.trim()?.takeIf(String::isNotEmpty),
                if (command.comment.isNullOrBlank()) ReviewCommentStatus.NONE else ReviewCommentStatus.PENDING,
                ReviewVisibilityStatus.ACTIVE,
                now,
                now,
            )
        }

        override fun save(review: Review): Review = review
        override fun findLatestSubmissionForUpdate(authorUserId: Long, restaurantId: Long) = latest
        override fun findOwnedActiveForUpdate(authorUserId: Long, reviewId: Long): Review? = null
        override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long {
            countedSince = since
            return recentReviewCount
        }
        override fun findByAuthorUserId(authorUserId: Long, cursor: ReviewCursor?, limit: Int) = emptyList<Review>()
    }

    private class RecordingTransactionManager(private val events: MutableList<String>) : PlatformTransactionManager {
        var commits = 0
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus().also { events += "transaction.begin" }
        override fun commit(status: TransactionStatus) { commits++; events += "transaction.commit" }
        override fun rollback(status: TransactionStatus) { events += "transaction.rollback" }
    }

    private fun ratings() = ReviewRatings(
        ReviewRating.GOOD, ReviewRating.VERY_GOOD, ReviewRating.GOOD,
        ReviewRating.GOOD, ReviewRating.NOT_OBSERVED, ReviewRating.GOOD,
    )

    private companion object {
        const val AUTHOR_ID = 7L
        const val RESTAURANT_ID = 10L
        val NOW: Instant = Instant.parse("2026-07-26T03:00:00Z")
    }
}
