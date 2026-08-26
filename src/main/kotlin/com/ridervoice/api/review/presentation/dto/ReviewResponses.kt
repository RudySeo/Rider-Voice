package com.ridervoice.api.review.presentation.dto

import com.ridervoice.api.review.domain.ReviewCommentStatus
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
    @field:Schema(format = "int64", description = "삭제·전체 제외를 포함한 전체 작성 건수")
    val authoredCount: Long,
    @field:Schema(format = "int64", description = "현재 공개 중인 활성 리뷰 건수")
    val publiclyVisibleCount: Long,
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
    val ratings: ReviewRatingsResponse,
    @field:Schema(nullable = true, description = "작성 즉시 공개되며 신고 또는 관리자 조치로 숨겨질 수 있습니다.")
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
