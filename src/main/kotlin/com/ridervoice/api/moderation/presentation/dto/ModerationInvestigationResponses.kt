package com.ridervoice.api.moderation.presentation.dto

import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

data class AdminReviewDetailResponse(
    @field:Schema(format = "int64") val reviewId: Long,
    val author: AdminReviewAuthorResponse,
    val restaurant: AdminReviewRestaurantResponse,
    val visitMonth: String,
    val ratings: AdminReviewRatingsResponse,
    @field:Schema(nullable = true) val comment: String?,
    val commentStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
    val historyStatus: ReviewHistoryStatus,
    val sequence: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminReviewAuthorResponse(
    @field:Schema(format = "int64") val userId: Long,
    val status: UserStatus,
    val activityMonths: Int,
    val publicReviewCount: Long,
)

data class AdminReviewRestaurantResponse(
    @field:Schema(format = "int64") val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus,
    @field:Schema(format = "int64") val pickupLocationId: Long,
    val pickupAddress: String,
)

data class AdminReviewRatingsResponse(
    val pickupSpaceCleanliness: ReviewRating,
    val packagingStability: ReviewRating,
    val orderReadiness: ReviewRating,
    val handoffAccuracy: ReviewRating,
    val staffInteraction: ReviewRating,
    val riderRespect: ReviewRating,
)

data class AdminRestaurantSearchPageResponse(
    val items: List<AdminRestaurantSearchItemResponse>,
    @field:Schema(nullable = true) val nextCursor: String?,
)

data class AdminRestaurantSearchItemResponse(
    @field:Schema(format = "int64") val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus,
    @field:Schema(format = "int64", nullable = true) val canonicalRestaurantId: Long?,
    @field:Schema(format = "int64") val pickupLocationId: Long,
    val standardAddress: String,
    @field:Schema(nullable = true) val detailAddress: String?,
    val createdAt: Instant,
)

data class AdminRestaurantDetailResponse(
    @field:Schema(format = "int64") val restaurantId: Long,
    val name: String,
    val normalizedName: String,
    val status: RestaurantStatus,
    @field:Schema(format = "int64", nullable = true) val canonicalRestaurantId: Long?,
    val pickupLocation: AdminPickupLocationResponse,
    val externalReferences: List<AdminExternalReferenceResponse>,
    val platforms: Set<DeliveryPlatform>,
    val pendingReportCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminPickupLocationResponse(
    @field:Schema(format = "int64") val pickupLocationId: Long,
    val standardAddress: String,
    @field:Schema(nullable = true) val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class AdminExternalReferenceResponse(
    val provider: RestaurantExternalProvider,
    val externalPlaceId: String,
)

data class ModerationAuditPageResponse(
    val items: List<ModerationAuditResponse>,
    @field:Schema(nullable = true) val nextCursor: String?,
)

data class ModerationAuditResponse(
    @field:Schema(format = "int64") val auditId: Long,
    @field:Schema(format = "int64") val actorUserId: Long,
    val action: ModerationAuditAction,
    val targetType: ModerationTargetType,
    @field:Schema(format = "int64") val targetId: Long,
    @field:Schema(nullable = true) val reason: String?,
    val beforeState: String,
    val afterState: String,
    val occurredAt: Instant,
    val createdAt: Instant,
)
