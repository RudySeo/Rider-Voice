package com.ridervoice.api.moderation.application.model

import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import java.math.BigDecimal
import java.time.Instant

data class AdminReviewDetailResult(
    val reviewId: Long,
    val authorUserId: Long,
    val authorStatus: UserStatus,
    val authorActivityMonths: Int,
    val authorPublicReviewCount: Long,
    val restaurantId: Long,
    val restaurantName: String,
    val restaurantStatus: RestaurantStatus,
    val pickupLocationId: Long,
    val pickupAddress: String,
    val visitMonth: VisitMonth,
    val ratings: ReviewRatings,
    val comment: String?,
    val commentStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
    val historyStatus: ReviewHistoryStatus,
    val sequence: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminRestaurantCursor(val createdAt: Instant, val restaurantId: Long)

data class AdminRestaurantSearchItemResult(
    val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus,
    val canonicalRestaurantId: Long?,
    val pickupLocationId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val createdAt: Instant,
)

data class AdminRestaurantSearchPageResult(
    val items: List<AdminRestaurantSearchItemResult>,
    val nextCursor: AdminRestaurantCursor?,
)

data class AdminExternalReferenceResult(
    val provider: RestaurantExternalProvider,
    val externalPlaceId: String,
)

data class AdminRestaurantDetailResult(
    val restaurantId: Long,
    val name: String,
    val normalizedName: String,
    val status: RestaurantStatus,
    val canonicalRestaurantId: Long?,
    val pickupLocationId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val externalReferences: List<AdminExternalReferenceResult>,
    val platforms: Set<DeliveryPlatform>,
    val pendingReportCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ModerationAuditCursor(val createdAt: Instant, val auditId: Long)

data class ModerationAuditResult(
    val auditId: Long,
    val actorUserId: Long,
    val action: ModerationAuditAction,
    val targetType: ModerationTargetType,
    val targetId: Long,
    val reason: String?,
    val beforeState: String,
    val afterState: String,
    val occurredAt: Instant,
    val createdAt: Instant,
)

data class ModerationAuditPageResult(
    val items: List<ModerationAuditResult>,
    val nextCursor: ModerationAuditCursor?,
)
