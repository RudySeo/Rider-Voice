package com.ridervoice.api.review.application

import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.restaurant.application.port.`in`.ResolveValidatedRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ValidateRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ValidatedRestaurantTarget
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.CreateReviewUseCase
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateRepository
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateSnapshot
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.SavedReviewSnapshot
import com.ridervoice.api.review.domain.ReviewHistoryPolicy
import com.ridervoice.api.review.domain.ReviewSubmissionPolicy
import com.ridervoice.api.review.domain.VisitMonthPolicy
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

@Service
internal class ReviewCreateService(
    private val targetValidator: ValidateRestaurantTargetUseCase,
    private val targetResolver: ResolveValidatedRestaurantTargetUseCase,
    private val reviews: ReviewRepository,
    private val states: AuthorRestaurantReviewStateRepository,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) : CreateReviewUseCase {

    private val transaction = TransactionTemplate(transactionManager)

    override fun create(command: CreateReviewCommand): ReviewResult {
        val validatedTarget = targetValidator.validate(command.restaurantTarget)
        var lastConcurrentFailure: DataAccessException? = null

        repeat(MAX_TRANSACTION_ATTEMPTS) {
            try {
                return transaction.execute {
                    createInTransaction(command, validatedTarget)
                }
            } catch (failure: DataAccessException) {
                if (failure !is DataIntegrityViolationException && failure !is TransientDataAccessException) {
                    throw failure
                }
                lastConcurrentFailure = failure
            }
        }
        throw lastConcurrentFailure ?: IllegalStateException("Review create transaction did not run")
    }

    private fun createInTransaction(
        command: CreateReviewCommand,
        validatedTarget: ValidatedRestaurantTarget,
    ): ReviewResult {
        val submittedAt = clock.instant()
        VisitMonthPolicy.requireAllowed(command.visitMonth, clock)

        val recentReviewCount = reviews.countByAuthorUserIdSince(
            command.authorUserId,
            submittedAt.minus(REVIEW_RATE_LIMIT_WINDOW),
        )
        if (recentReviewCount >= MAX_REVIEWS_PER_WINDOW) {
            throw StateConflictException("At most 10 reviews can be submitted in 24 hours")
        }

        val resolvedTarget = targetResolver.resolve(validatedTarget)
        val currentState = states.findForUpdate(command.authorUserId, resolvedTarget.restaurantId)
        if (!ReviewSubmissionPolicy.canSubmit(currentState?.lastSubmittedAt, submittedAt)) {
            throw StateConflictException("A new review can be submitted 90 days after the last submission")
        }

        val sequence = currentState?.lastSequence?.let(Math::incrementExact) ?: 1L
        val review = reviews.create(
            NewReviewPersistenceCommand(
                authorUserId = command.authorUserId,
                restaurantId = resolvedTarget.restaurantId,
                visitMonth = command.visitMonth,
                ratings = command.ratings,
                comment = command.comment,
                sequence = sequence,
            ),
        )
        val savedState = states.save(
            AuthorRestaurantReviewStateSnapshot(
                stateId = currentState?.stateId,
                authorUserId = command.authorUserId,
                restaurantId = resolvedTarget.restaurantId,
                lastSubmittedAt = submittedAt,
                lastSequence = sequence,
                currentReviewId = review.reviewId,
            ),
        )
        return review.toResult(savedState.currentReviewId)
    }

    private fun SavedReviewSnapshot.toResult(currentReviewId: Long?): ReviewResult = ReviewResult(
        reviewId = reviewId,
        restaurant = restaurant,
        visitMonth = visitMonth,
        ratings = ratings,
        comment = comment,
        commentModerationStatus = commentModerationStatus,
        visibilityStatus = visibilityStatus,
        historyStatus = ReviewHistoryPolicy.classify(reviewId, currentReviewId),
        sequence = sequence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val MAX_REVIEWS_PER_WINDOW = 10L
        const val MAX_TRANSACTION_ATTEMPTS = 4
        val REVIEW_RATE_LIMIT_WINDOW: Duration = Duration.ofHours(24)
    }
}
