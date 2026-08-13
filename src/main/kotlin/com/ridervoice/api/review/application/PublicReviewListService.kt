package com.ridervoice.api.review.application

import com.ridervoice.api.restaurant.application.port.`in`.ResolveReadableRestaurantUseCase
import com.ridervoice.api.review.application.model.PublicAuthorActivityInput
import com.ridervoice.api.review.application.model.PublicReviewAuthorActivityResult
import com.ridervoice.api.review.application.model.PublicReviewListItemInput
import com.ridervoice.api.review.application.model.PublicReviewListItemResult
import com.ridervoice.api.review.application.model.PublicReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsUseCase
import com.ridervoice.api.review.application.port.out.PublicReviewQuery
import com.ridervoice.api.review.domain.ReviewCommentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
internal class PublicReviewListService(
    private val reviews: PublicReviewQuery,
    private val resolveRestaurant: ResolveReadableRestaurantUseCase,
    private val clock: Clock,
) : ListPublicRestaurantReviewsUseCase {

    @Transactional(readOnly = true)
    override fun list(command: ListPublicRestaurantReviewsCommand): PublicReviewListResult {
        val restaurantId = resolveRestaurant.resolve(command.restaurantId)
        val page = reviews.findActiveByRestaurantId(
            restaurantId = restaurantId,
            cursor = command.cursor,
            limit = command.size + 1,
        )
        val visibleItems = page.take(command.size)
        val activities = reviews.findAuthorActivities(
            visibleItems.mapTo(linkedSetOf(), PublicReviewListItemInput::authorUserId),
        ).associateBy(PublicAuthorActivityInput::authorUserId)

        return PublicReviewListResult(
            items = visibleItems.map { item -> item.toResult(requireNotNull(activities[item.authorUserId])) },
            nextCursor = if (page.size > command.size) {
                visibleItems.last().let { ReviewCursor(it.createdAt, it.reviewId) }
            } else {
                null
            },
        )
    }

    private fun PublicReviewListItemInput.toResult(
        activity: PublicAuthorActivityInput,
    ) = PublicReviewListItemResult(
        reviewId = reviewId,
        visitMonth = visitMonth,
        ratings = ratings,
        comment = comment.takeIf { commentModerationStatus == ReviewCommentStatus.PUBLISHED },
        authorActivity = PublicReviewAuthorActivityResult(
            activityMonths = activityMonths(activity),
            publicReviewCount = activity.publicReviewCount,
        ),
        createdAt = createdAt,
        verificationStatus = VERIFICATION_STATUS,
        verificationNotice = VERIFICATION_NOTICE,
    )

    private fun activityMonths(activity: PublicAuthorActivityInput): Int {
        val firstMonth = YearMonth.from(activity.firstPublicReviewAt.atZone(ZoneOffset.UTC))
        val currentMonth = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC))
        return (ChronoUnit.MONTHS.between(firstMonth, currentMonth) + 1).coerceAtLeast(1).toInt()
    }

    companion object {
        const val VERIFICATION_STATUS = "UNVERIFIED"
        const val VERIFICATION_NOTICE = "라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다."
    }
}
