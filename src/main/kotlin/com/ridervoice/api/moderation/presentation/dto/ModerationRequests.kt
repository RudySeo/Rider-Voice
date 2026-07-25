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

data class CreateReviewReportRequest(
    @field:NotNull
    val reason: ReviewReportReason?,
    @field:Schema(nullable = true)
    val details: String? = null,
)

data class CreateRestaurantInfoReportRequest(
    @field:NotNull
    val reason: RestaurantInfoReportReason?,
    @field:Schema(nullable = true)
    val details: String? = null,
)

data class CommentDecisionRequest(
    @field:NotNull
    val decision: CommentModerationDecision?,
)

data class ReviewReportDecisionRequest(
    @field:NotNull
    val decision: ReviewReportDecision?,
    @field:Schema(nullable = true, description = "관리자 결정 사유")
    val reason: String? = null,
)

data class RestaurantInfoReportDecisionRequest(
    @field:NotNull
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
