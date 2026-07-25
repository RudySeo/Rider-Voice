package com.ridervoice.api.review.presentation.dto

import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewHistoryStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ReviewResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
    val restaurant: ReviewRestaurantResponse,
    @field:Schema(pattern = VISIT_MONTH_PATTERN, example = "2026-07")
    val visitMonth: String,
    val ratings: ReviewRatingsResponse,
    @field:Schema(nullable = true)
    val comment: String?,
    val commentModerationStatus: ReviewCommentStatus,
    val visibilityStatus: ReviewVisibilityStatus,
    val historyStatus: ReviewHistoryStatus,
    @field:Schema(format = "int64")
    val sequence: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ReviewRestaurantResponse(
    @field:Schema(format = "int64")
    val restaurantId: Long,
    val name: String,
    val address: String,
)

data class ReviewRatingsResponse(
    val pickupSpaceCleanliness: ReviewRating,
    val packagingStability: ReviewRating,
    val orderReadiness: ReviewRating,
    val handoffAccuracy: ReviewRating,
    val staffInteraction: ReviewRating,
    val riderRespect: ReviewRating,
)

data class MyReviewListResponse(
    val items: List<ReviewResponse>,
    @field:Schema(description = "createdAt과 reviewId 기반 opaque cursor", nullable = true)
    val nextCursor: String?,
)

data class DeleteReviewResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
)
