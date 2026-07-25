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
    fun `validates the external target before opening the atomic restaurant and review transaction`() {
        val fixture = fixture()

        val result = fixture.service.create(command(comment = "  검수할 의견  "))

        assertThat(fixture.events).startsWith("validate", "transaction.begin", "resolve", "review.save", "state.save")
        assertThat(fixture.transactions.commits).isEqualTo(1)
        assertThat(fixture.transactions.rollbacks).isZero()
        assertThat(result.comment).isEqualTo("검수할 의견")
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PENDING)
        assertThat(result.historyStatus).isEqualTo(ReviewHistoryStatus.CURRENT)
        assertThat(fixture.states.state?.currentReviewId).isEqualTo(result.reviewId)
    }

    @Test
    fun `accepts the ninety day boundary and later while rejecting the instant before it`() {
        val lastSubmittedAt = NOW.minus(Duration.ofDays(90))

        val before = fixture(
            now = NOW.minusNanos(1),
            initialState = state(lastSubmittedAt, sequence = 1L, currentReviewId = null),
        )
        assertThatThrownBy { before.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
        assertThat(before.reviews.saved).isEmpty()

        val boundary = fixture(
            now = NOW,
            initialState = state(lastSubmittedAt, sequence = 1L, currentReviewId = 90L),
        )
        val boundaryResult = boundary.service.create(command())
        assertThat(boundaryResult.sequence).isEqualTo(2L)

        val after = fixture(
            now = NOW.plusSeconds(1),
            initialState = state(lastSubmittedAt, sequence = 4L, currentReviewId = 91L),
        )
        val afterResult = after.service.create(command())
        assertThat(afterResult.sequence).isEqualTo(5L)
    }

    @Test
    fun `blocks the eleventh account review in the recent twenty four hours`() {
        val fixture = fixture(recentReviewCount = 10L)

        assertThatThrownBy { fixture.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("24 hours")

        assertThat(fixture.reviews.countedSince).isEqualTo(NOW.minus(Duration.ofHours(24)))
        assertThat(fixture.events).doesNotContain("resolve", "review.save", "state.save")
        assertThat(fixture.transactions.rollbacks).isEqualTo(1)
    }

    @Test
    fun `keeps the previous current review as history and replaces only the state pointer`() {
        val fixture = fixture(
            initialState = state(
                lastSubmittedAt = NOW.minus(Duration.ofDays(91)),
                sequence = 3L,
                currentReviewId = 77L,
            ),
        )

        val result = fixture.service.create(command(comment = "  "))

        assertThat(result.sequence).isEqualTo(4L)
        assertThat(result.comment).isNull()
        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.NONE)
        assertThat(fixture.reviews.deleted).isEmpty()
        assertThat(fixture.states.state?.currentReviewId).isEqualTo(result.reviewId)
        assertThat(fixture.states.state?.lastSequence).isEqualTo(4L)
    }

    @Test
    fun `rolls back restaurant resolution and review persistence when state persistence fails`() {
        val failure = IllegalStateException("state write failed")
        val fixture = fixture(stateSaveFailure = failure)

        assertThatThrownBy { fixture.service.create(command()) }
            .isSameAs(failure)

        assertThat(fixture.transactions.commits).isZero()
        assertThat(fixture.transactions.rollbacks).isEqualTo(1)
        assertThat(fixture.events).containsSubsequence("resolve", "review.save", "state.save")
    }

    @Test
    fun `retries a concurrent first state unique race and reloads the winner under lock`() {
        val winner = state(NOW, sequence = 1L, currentReviewId = 501L)
        val fixture = fixture(
            stateSaveFailure = DataIntegrityViolationException("concurrent state winner"),
            winnerStateAfterFailure = winner,
        )

        assertThatThrownBy { fixture.service.create(command()) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("90 days")

        assertThat(fixture.events.count { it == "validate" }).isEqualTo(1)
        assertThat(fixture.events.count { it == "transaction.begin" }).isEqualTo(2)
        assertThat(fixture.states.findForUpdateCalls).isEqualTo(2)
        assertThat(fixture.transactions.rollbacks).isEqualTo(2)
    }

    private fun fixture(
        now: Instant = NOW,
        initialState: AuthorRestaurantReviewStateSnapshot? = null,
        recentReviewCount: Long = 0L,
        stateSaveFailure: RuntimeException? = null,
        winnerStateAfterFailure: AuthorRestaurantReviewStateSnapshot? = null,
    ): Fixture {
        val events = mutableListOf<String>()
        val reviews = FakeReviewRepository(events, recentReviewCount, now)
        val states = FakeStateRepository(
            events,
            initialState,
            stateSaveFailure,
            winnerStateAfterFailure,
        )
        val transactions = RecordingTransactionManager(events)
        val validator = ValidateRestaurantTargetUseCase {
            events += "validate"
            ValidatedExistingRestaurantTarget(RESTAURANT_ID)
        }
        val resolver = ResolveValidatedRestaurantTargetUseCase {
            events += "resolve"
            ResolvedRestaurantTargetResult(RESTAURANT_ID)
        }
        val service = ReviewCreateService(
            targetValidator = validator,
            targetResolver = resolver,
            reviews = reviews,
            states = states,
            transactionManager = transactions,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Fixture(service, events, reviews, states, transactions)
    }

    private fun command(comment: String? = null) = CreateReviewCommand(
        authorUserId = AUTHOR_ID,
        restaurantTarget = ExistingRestaurantTargetCommand(RESTAURANT_ID),
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ReviewRatings(
            pickupSpaceCleanliness = ReviewRating.GOOD,
            packagingStability = ReviewRating.VERY_GOOD,
            orderReadiness = ReviewRating.GOOD,
            handoffAccuracy = ReviewRating.GOOD,
            staffInteraction = ReviewRating.NOT_OBSERVED,
            riderRespect = ReviewRating.GOOD,
        ),
        comment = comment,
    )

    private fun state(
        lastSubmittedAt: Instant,
        sequence: Long,
        currentReviewId: Long?,
    ) = AuthorRestaurantReviewStateSnapshot(
        stateId = 30L,
        authorUserId = AUTHOR_ID,
        restaurantId = RESTAURANT_ID,
        lastSubmittedAt = lastSubmittedAt,
        lastSequence = sequence,
        currentReviewId = currentReviewId,
    )

    private data class Fixture(
        val service: ReviewCreateService,
        val events: MutableList<String>,
        val reviews: FakeReviewRepository,
        val states: FakeStateRepository,
        val transactions: RecordingTransactionManager,
    )

    private class FakeReviewRepository(
        private val events: MutableList<String>,
        private val recentReviewCount: Long,
        private val now: Instant,
    ) : ReviewRepository {
        val saved = mutableListOf<Review>()
        val deleted = mutableListOf<Review>()
        var countedSince: Instant? = null

        override fun create(command: NewReviewPersistenceCommand): SavedReviewSnapshot {
            events += "review.save"
            return SavedReviewSnapshot(
                reviewId = 100L + saved.size,
                restaurant = ReviewRestaurantSummary(
                    restaurantId = command.restaurantId,
                    name = "테스트 브랜드",
                    address = "서울 강남구 테헤란로 1",
                ),
                visitMonth = command.visitMonth,
                ratings = command.ratings,
                comment = command.comment?.trim()?.takeIf(String::isNotEmpty),
                commentModerationStatus = if (command.comment.isNullOrBlank()) {
                    ReviewCommentStatus.NONE
                } else {
                    ReviewCommentStatus.PENDING
                },
                visibilityStatus = com.ridervoice.api.review.domain.ReviewVisibilityStatus.ACTIVE,
                sequence = command.sequence,
                createdAt = now,
                updatedAt = now,
            )
        }

        override fun save(review: Review): Review = review.also { saved += it }

        override fun findOwnedCurrentForUpdate(authorUserId: Long, reviewId: Long): Review? = null

        override fun delete(review: Review) {
            deleted += review
        }

        override fun countByAuthorUserIdSince(authorUserId: Long, since: Instant): Long {
            countedSince = since
            return recentReviewCount
        }

        override fun findByAuthorUserId(
            authorUserId: Long,
            cursor: ReviewCursor?,
            limit: Int,
        ): List<Review> = emptyList()
    }

    private class FakeStateRepository(
        private val events: MutableList<String>,
        initialState: AuthorRestaurantReviewStateSnapshot?,
        private var saveFailure: RuntimeException?,
        private val winnerStateAfterFailure: AuthorRestaurantReviewStateSnapshot?,
    ) : AuthorRestaurantReviewStateRepository {
        var state: AuthorRestaurantReviewStateSnapshot? = initialState
        var findForUpdateCalls: Int = 0

        override fun findForUpdate(
            authorUserId: Long,
            restaurantId: Long,
        ): AuthorRestaurantReviewStateSnapshot? {
            findForUpdateCalls++
            return state
        }

        override fun save(
            state: AuthorRestaurantReviewStateSnapshot,
        ): AuthorRestaurantReviewStateSnapshot {
            events += "state.save"
            saveFailure?.let { failure ->
                saveFailure = null
                this.state = winnerStateAfterFailure
                throw failure
            }
            return state.copy(stateId = state.stateId ?: 30L).also { this.state = it }
        }
    }

    private class RecordingTransactionManager(
        private val events: MutableList<String>,
    ) : PlatformTransactionManager {
        var commits: Int = 0
        var rollbacks: Int = 0

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            events += "transaction.begin"
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) {
            commits++
            events += "transaction.commit"
        }

        override fun rollback(status: TransactionStatus) {
            rollbacks++
            events += "transaction.rollback"
        }
    }

    companion object {
        private const val AUTHOR_ID = 7L
        private const val RESTAURANT_ID = 10L
        private val NOW = Instant.parse("2026-07-26T03:00:00Z")

    }
}
