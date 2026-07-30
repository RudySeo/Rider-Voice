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

data class PublicReviewListResponse(
    val items: List<PublicReviewListItemResponse>,
    @field:Schema(description = "createdAt과 reviewId 기반 opaque cursor", nullable = true)
    val nextCursor: String?,
)

data class PublicReviewListItemResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
    @field:Schema(pattern = VISIT_MONTH_PATTERN, example = "2026-07")
    val visitMonth: String,
    val current: Boolean,
    val ratings: ReviewRatingsResponse,
    @field:Schema(nullable = true, description = "관리자 승인된 의견만 노출됩니다.")
    val comment: String?,
    val authorActivity: PublicReviewAuthorActivityResponse,
    val createdAt: Instant,
    @field:Schema(allowableValues = ["UNVERIFIED"])
    val verificationStatus: String,
    @field:Schema(example = "라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.")
    val verificationNotice: String,
)

data class PublicReviewAuthorActivityResponse(
    val activityMonths: Int,
    @field:Schema(format = "int64")
    val publicReviewCount: Long,
)

data class DeleteReviewResponse(
    @field:Schema(format = "int64")
    val reviewId: Long,
)
