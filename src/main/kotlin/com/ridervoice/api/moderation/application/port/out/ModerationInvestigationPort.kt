package com.ridervoice.api.moderation.application.port.out

import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.model.ModerationAuditCursor
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import java.math.BigDecimal
import java.time.Instant

interface ModerationInvestigationQuery {
    fun findReview(reviewId: Long): StoredAdminReviewDetail?
    fun searchRestaurants(
        normalizedQuery: String,
        status: RestaurantStatus?,
        cursor: AdminRestaurantCursor?,
        limit: Int,
    ): List<StoredAdminRestaurantDetail>
    fun findRestaurant(restaurantId: Long): StoredAdminRestaurantDetail?
    fun findAudits(
        targetType: ModerationTargetType?,
        targetId: Long?,
        actorUserId: Long?,
        action: ModerationAuditAction?,
        cursor: ModerationAuditCursor?,
        limit: Int,
    ): List<StoredModerationAudit>
}

data class StoredAdminReviewDetail(
    val reviewId: Long,
    val authorUserId: Long,
    val authorStatus: UserStatus,
    val firstPublicReviewAt: Instant,
    val publicReviewCount: Long,
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
    val active: Boolean,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class StoredAdminExternalReference(
    val provider: RestaurantExternalProvider,
    val externalPlaceId: String,
)

data class StoredAdminRestaurantDetail(
    val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus,
    val canonicalRestaurantId: Long?,
    val pickupLocationId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val externalReferences: List<StoredAdminExternalReference>,
    val platforms: Set<DeliveryPlatform>,
    val pendingReportCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
