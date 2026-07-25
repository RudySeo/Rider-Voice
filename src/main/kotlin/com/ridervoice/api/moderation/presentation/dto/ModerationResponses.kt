package com.ridervoice.api.moderation.presentation.dto

import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ReviewReportResponse(
    @field:Schema(format = "int64")
    val reportId: Long,
    @field:Schema(format = "int64")
    val reviewId: Long,
    val reason: ReviewReportReason,
    val status: ReportStatus,
    @field:Schema(nullable = true)
    val decision: ReviewReportDecision?,
    val createdAt: Instant,
    @field:Schema(nullable = true)
    val decidedAt: Instant?,
)

data class RestaurantInfoReportResponse(
    @field:Schema(format = "int64")
    val reportId: Long,
    @field:Schema(format = "int64")
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val status: ReportStatus,
    @field:Schema(nullable = true)
    val decision: RestaurantInfoReportDecision?,
    val createdAt: Instant,
    @field:Schema(nullable = true)
    val decidedAt: Instant?,
)

data class PendingReviewCommentResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
    @field:Schema(format = "int64")
    val authorUserId: Long,
    val comment: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingReviewCommentPageResponse(
    val items: List<PendingReviewCommentResponse>,
    @field:Schema(description = "createdAt과 reviewId 기반 opaque cursor", nullable = true)
    val nextCursor: String?,
)

data class ReviewCommentDecisionResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
    val commentModerationStatus: ReviewCommentStatus,
    val decidedAt: Instant,
)

data class PendingReviewReportResponse(
    @field:Schema(format = "int64")
    val reportId: Long,
    @field:Schema(format = "int64")
    val reporterUserId: Long,
    @field:Schema(format = "int64")
    val reviewId: Long,
    val reason: ReviewReportReason,
    @field:Schema(nullable = true)
    val details: String?,
    val createdAt: Instant,
)

data class PendingReviewReportPageResponse(
    val items: List<PendingReviewReportResponse>,
    @field:Schema(description = "createdAt과 reportId 기반 opaque cursor", nullable = true)
    val nextCursor: String?,
)

data class PendingRestaurantInfoReportResponse(
    @field:Schema(format = "int64")
    val reportId: Long,
    @field:Schema(format = "int64")
    val reporterUserId: Long,
    @field:Schema(format = "int64")
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    @field:Schema(nullable = true)
    val details: String?,
    val createdAt: Instant,
)

data class PendingRestaurantInfoReportPageResponse(
    val items: List<PendingRestaurantInfoReportResponse>,
    @field:Schema(description = "createdAt과 reportId 기반 opaque cursor", nullable = true)
    val nextCursor: String?,
)

data class RestaurantMergeResponse(
    @field:Schema(format = "int64")
    val restaurantId: Long,
    val status: RestaurantStatus,
    @field:Schema(format = "int64")
    val canonicalRestaurantId: Long,
    val completedAt: Instant,
)

data class RestaurantPickupRelinkResponse(
    @field:Schema(format = "int64")
    val restaurantId: Long,
    @field:Schema(format = "int64")
    val pickupLocationId: Long,
    val completedAt: Instant,
)
