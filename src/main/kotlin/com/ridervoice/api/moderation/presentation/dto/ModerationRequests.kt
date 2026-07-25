package com.ridervoice.api.moderation.presentation.dto

import com.ridervoice.api.moderation.domain.CommentModerationDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateReviewReportRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val reason: ReviewReportReason?,
    @field:Schema(nullable = true)
    val details: String? = null,
)

data class CreateRestaurantInfoReportRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val reason: RestaurantInfoReportReason?,
    @field:Schema(nullable = true)
    val details: String? = null,
)

data class CommentDecisionRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val decision: CommentModerationDecision?,
)

data class ReviewReportDecisionRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val decision: ReviewReportDecision?,
    @field:Schema(nullable = true, description = "관리자 결정 사유")
    val reason: String? = null,
)

data class RestaurantInfoReportDecisionRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val decision: RestaurantInfoReportDecision?,
    @field:Schema(nullable = true, description = "관리자 결정 사유")
    val reason: String? = null,
)

data class ModerationPageRequest(
    @field:Schema(description = "createdAt과 ID 기반 opaque cursor", nullable = true)
    val cursor: String? = null,
    @field:Min(1)
    @field:Max(50)
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "50")
    val size: Int = 20,
)

data class MergeRestaurantRequest(
    @field:NotNull
    @field:Positive
    @field:Schema(format = "int64", nullable = false)
    val canonicalRestaurantId: Long?,
    @field:Schema(nullable = true, description = "관리자 병합 사유")
    val reason: String? = null,
)

data class RelinkRestaurantPickupLocationRequest(
    @field:NotNull
    @field:Positive
    @field:Schema(format = "int64", nullable = false)
    val pickupLocationId: Long?,
    @field:Schema(nullable = true, description = "관리자 픽업 장소 정정 사유")
    val reason: String? = null,
)
