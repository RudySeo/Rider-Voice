package com.ridervoice.api.moderation.presentation.dto

import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import com.ridervoice.api.moderation.application.port.`in`.RestaurantStatusAction
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue

data class CreateReviewReportRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val reason: ReviewReportReason?,
    @field:Schema(nullable = true)
    @field:Size(max = 1000)
    val details: String? = null,
)

data class CreateRestaurantInfoReportRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val reason: RestaurantInfoReportReason?,
    @field:Schema(nullable = true)
    @field:Size(max = 1000)
    val details: String? = null,
)

data class ReviewReportDecisionRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val decision: ReviewReportDecision?,
    @field:Schema(nullable = true, description = "관리자 결정 사유")
    @field:Size(max = 500)
    val reason: String? = null,
)

data class RestaurantInfoReportDecisionRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val decision: RestaurantInfoReportDecision?,
    @field:Schema(nullable = true, description = "관리자 결정 사유")
    @field:Size(max = 500)
    val reason: String? = null,
    @field:Valid
    @field:Schema(nullable = true)
    val correction: RestaurantInfoCorrectionRequest? = null,
) {
    @get:AssertTrue(message = "DISMISS forbids correction and RESOLVE requires correction")
    @get:Schema(hidden = true)
    val correctionMatchesDecision: Boolean
        get() = (decision == RestaurantInfoReportDecision.DISMISS && correction == null) ||
            (decision == RestaurantInfoReportDecision.RESOLVE && correction != null)
}

enum class RestaurantInfoCorrectionType {
    RENAME,
    RELINK_EXISTING_PICKUP,
    RELINK_VERIFIED_ADDRESS,
    CLOSE,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = RenameRestaurantCorrectionRequest::class, name = "RENAME"),
    JsonSubTypes.Type(value = RelinkExistingPickupCorrectionRequest::class, name = "RELINK_EXISTING_PICKUP"),
    JsonSubTypes.Type(value = RelinkVerifiedAddressCorrectionRequest::class, name = "RELINK_VERIFIED_ADDRESS"),
    JsonSubTypes.Type(value = CloseRestaurantCorrectionRequest::class, name = "CLOSE"),
)
@Schema(
    discriminatorProperty = "type",
    oneOf = [
        RenameRestaurantCorrectionRequest::class,
        RelinkExistingPickupCorrectionRequest::class,
        RelinkVerifiedAddressCorrectionRequest::class,
        CloseRestaurantCorrectionRequest::class,
    ],
    discriminatorMapping = [
        DiscriminatorMapping(value = "RENAME", schema = RenameRestaurantCorrectionRequest::class),
        DiscriminatorMapping(value = "RELINK_EXISTING_PICKUP", schema = RelinkExistingPickupCorrectionRequest::class),
        DiscriminatorMapping(value = "RELINK_VERIFIED_ADDRESS", schema = RelinkVerifiedAddressCorrectionRequest::class),
        DiscriminatorMapping(value = "CLOSE", schema = CloseRestaurantCorrectionRequest::class),
    ],
)
sealed interface RestaurantInfoCorrectionRequest {
    @get:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val type: RestaurantInfoCorrectionType
}

data class RenameRestaurantCorrectionRequest(
    override val type: RestaurantInfoCorrectionType,
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val name: String,
) : RestaurantInfoCorrectionRequest

data class RelinkExistingPickupCorrectionRequest(
    override val type: RestaurantInfoCorrectionType,
    @field:Positive
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val pickupLocationId: Long,
) : RestaurantInfoCorrectionRequest

data class RelinkVerifiedAddressCorrectionRequest(
    override val type: RestaurantInfoCorrectionType,
    @field:NotBlank
    @field:Size(min = 2, max = 100)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val addressQuery: String,
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val selectedStandardAddress: String,
    @field:Size(max = 255) @field:Schema(nullable = true) val detailAddress: String? = null,
) : RestaurantInfoCorrectionRequest

data class CloseRestaurantCorrectionRequest(
    override val type: RestaurantInfoCorrectionType,
) : RestaurantInfoCorrectionRequest

data class ModerationPageRequest(
    @field:Schema(description = "createdAt과 ID 기반 opaque cursor", nullable = true)
    val cursor: String? = null,
    @field:Min(1)
    @field:Max(50)
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "50")
    val size: Int = 20,
)

data class RelinkRestaurantPickupLocationRequest(
    @field:NotNull
    @field:Positive
    @field:Schema(format = "int64", nullable = false)
    val pickupLocationId: Long?,
    @field:Schema(nullable = true, description = "관리자 픽업 장소 정정 사유")
    @field:Size(max = 500)
    val reason: String? = null,
)

data class RenameRestaurantRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 500)
    @field:Schema(nullable = true)
    val reason: String? = null,
)

data class ChangeRestaurantStatusRequest(
    @field:NotNull
    @field:Schema(nullable = false)
    val action: RestaurantStatusAction?,
    @field:Size(max = 500)
    @field:Schema(nullable = true)
    val reason: String? = null,
)

data class RelinkRestaurantVerifiedAddressRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 100)
    val addressQuery: String,
    @field:NotBlank
    @field:Size(max = 255)
    val selectedStandardAddress: String,
    @field:Size(max = 255)
    @field:Schema(nullable = true)
    val detailAddress: String? = null,
    @field:Size(max = 500)
    @field:Schema(nullable = true)
    val reason: String? = null,
)
