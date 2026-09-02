package com.ridervoice.api.review.presentation

import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand
import com.ridervoice.api.review.application.model.MyReviewListResult
import com.ridervoice.api.review.application.model.PublicReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewResult
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.review.presentation.dto.CreateReviewRequest
import com.ridervoice.api.review.presentation.dto.DeleteReviewResponse
import com.ridervoice.api.review.presentation.dto.ExistingRestaurantTargetRequest
import com.ridervoice.api.review.presentation.dto.KakaoRestaurantTargetRequest
import com.ridervoice.api.review.presentation.dto.ManualAddressRestaurantTargetRequest
import com.ridervoice.api.review.presentation.dto.ManualExistingLocationRestaurantTargetRequest
import com.ridervoice.api.review.presentation.dto.MyReviewListResponse
import com.ridervoice.api.review.presentation.dto.MyReviewsRequest
import com.ridervoice.api.review.presentation.dto.PublicReviewAuthorActivityResponse
import com.ridervoice.api.review.presentation.dto.PublicReviewListItemResponse
import com.ridervoice.api.review.presentation.dto.PublicReviewListResponse
import com.ridervoice.api.review.presentation.dto.PublicReviewsRequest
import com.ridervoice.api.review.presentation.dto.RestaurantTargetRequest
import com.ridervoice.api.review.presentation.dto.ReviewRatingsResponse
import com.ridervoice.api.review.presentation.dto.ReviewResponse
import com.ridervoice.api.review.presentation.dto.ReviewRestaurantResponse
import com.ridervoice.api.review.presentation.dto.UpdateReviewRequest
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

@Component
class ReviewHttpMapper {
    fun toCreateCommand(authorUserId: Long, request: CreateReviewRequest) = CreateReviewCommand(
        authorUserId = authorUserId,
        restaurantTarget = toRestaurantTargetCommand(requireNotNull(request.restaurantTarget)),
        visitMonth = VisitMonth.parse(requireNotNull(request.visitMonth)),
        ratings = request.toRatings(),
        comment = request.comment,
    )

    fun toUpdateCommand(authorUserId: Long, reviewId: Long, request: UpdateReviewRequest) = UpdateReviewCommand(
        authorUserId = authorUserId,
        reviewId = reviewId,
        ratings = request.toRatings(),
        comment = request.comment,
    )

    fun toListCommand(authorUserId: Long, request: MyReviewsRequest) = ListMyReviewsCommand(
        authorUserId = authorUserId,
        cursor = request.cursor?.let(::decodeCursor),
        size = request.size,
    )

    fun toPublicListCommand(
        restaurantId: Long,
        request: PublicReviewsRequest,
    ) = ListPublicRestaurantReviewsCommand(
        restaurantId = restaurantId,
        cursor = request.cursor?.let(::decodeCursor),
        size = request.size,
    )

    fun toReviewResponse(result: ReviewResult) = ReviewResponse(
        reviewId = result.reviewId,
        restaurant = ReviewRestaurantResponse(
            restaurantId = result.restaurant.restaurantId,
            name = result.restaurant.name,
            address = result.restaurant.address,
        ),
        visitMonth = result.visitMonth.toString(),
        ratings = result.ratings.toResponse(),
        comment = result.comment,
        commentModerationStatus = result.commentModerationStatus,
        visibilityStatus = result.visibilityStatus,
        createdAt = result.createdAt,
        updatedAt = result.updatedAt,
    )

    fun toMyReviewListResponse(result: MyReviewListResult) = MyReviewListResponse(
        items = result.items.map(::toReviewResponse),
        nextCursor = result.nextCursor?.let(::encodeCursor),
        authoredCount = result.authoredCount,
        publiclyVisibleCount = result.publiclyVisibleCount,
    )

    fun toPublicReviewListResponse(result: PublicReviewListResult) = PublicReviewListResponse(
        items = result.items.map { item ->
            PublicReviewListItemResponse(
                reviewId = item.reviewId,
                visitMonth = item.visitMonth.toString(),
                ratings = item.ratings.toResponse(),
                comment = item.comment,
                authorActivity = PublicReviewAuthorActivityResponse(
                    activityMonths = item.authorActivity.activityMonths,
                    publicReviewCount = item.authorActivity.publicReviewCount,
                ),
                createdAt = item.createdAt,
            )
        },
        nextCursor = result.nextCursor?.let(::encodeCursor),
    )

    fun toDeleteReviewResponse(result: DeleteReviewResult) = DeleteReviewResponse(result.reviewId)

    private fun toRestaurantTargetCommand(request: RestaurantTargetRequest): RestaurantTargetCommand = when (request) {
        is ExistingRestaurantTargetRequest -> ExistingRestaurantTargetCommand(request.restaurantId)
        is KakaoRestaurantTargetRequest -> KakaoRestaurantTargetCommand(request.query, request.kakaoPlaceId)
        is ManualExistingLocationRestaurantTargetRequest -> ManualExistingLocationRestaurantTargetCommand(
            pickupLocationId = request.pickupLocationId,
            name = request.name,
            platforms = requireNotNull(request.platforms),
        )
        is ManualAddressRestaurantTargetRequest -> ManualAddressRestaurantTargetCommand(
            addressQuery = request.addressQuery,
            selectedStandardAddress = request.selectedStandardAddress,
            detailAddress = request.detailAddress,
            name = request.name,
            platforms = requireNotNull(request.platforms),
        )
    }

    private fun CreateReviewRequest.toRatings() = ReviewRatings(
        pickupSpaceCleanliness = requireNotNull(pickupSpaceCleanliness),
        packagingStability = requireNotNull(packagingStability),
        orderReadiness = requireNotNull(orderReadiness),
        handoffAccuracy = requireNotNull(handoffAccuracy),
        staffInteraction = requireNotNull(staffInteraction),
        riderRespect = requireNotNull(riderRespect),
    )

    private fun UpdateReviewRequest.toRatings() = ReviewRatings(
        pickupSpaceCleanliness = requireNotNull(pickupSpaceCleanliness),
        packagingStability = requireNotNull(packagingStability),
        orderReadiness = requireNotNull(orderReadiness),
        handoffAccuracy = requireNotNull(handoffAccuracy),
        staffInteraction = requireNotNull(staffInteraction),
        riderRespect = requireNotNull(riderRespect),
    )

    private fun ReviewRatings.toResponse() = ReviewRatingsResponse(
        pickupSpaceCleanliness = pickupSpaceCleanliness,
        packagingStability = packagingStability,
        orderReadiness = orderReadiness,
        handoffAccuracy = handoffAccuracy,
        staffInteraction = staffInteraction,
        riderRespect = riderRespect,
    )

    private fun encodeCursor(cursor: ReviewCursor): String {
        val rawCursor = "${cursor.createdAt}|${cursor.reviewId}"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rawCursor.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeCursor(encodedCursor: String): ReviewCursor = try {
        val rawCursor = String(Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8)
        val separator = rawCursor.lastIndexOf('|')
        require(separator > 0 && separator < rawCursor.lastIndex) { "Invalid review cursor" }
        ReviewCursor(
            createdAt = Instant.parse(rawCursor.substring(0, separator)),
            reviewId = rawCursor.substring(separator + 1).toLong(),
        )
    } catch (exception: RuntimeException) {
        throw IllegalArgumentException("Invalid review cursor", exception)
    }
}
