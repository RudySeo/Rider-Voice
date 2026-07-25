package com.ridervoice.api.review.presentation.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.review.domain.ReviewRating
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

const val VISIT_MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$"

data class CreateReviewRequest(
    @field:Valid
    @field:NotNull
    val restaurantTarget: RestaurantTargetRequest?,
    @field:NotBlank
    @field:Pattern(regexp = VISIT_MONTH_PATTERN)
    @field:Schema(pattern = VISIT_MONTH_PATTERN, example = "2026-07")
    val visitMonth: String?,
    @field:NotNull
    val pickupSpaceCleanliness: ReviewRating?,
    @field:NotNull
    val packagingStability: ReviewRating?,
    @field:NotNull
    val orderReadiness: ReviewRating?,
    @field:NotNull
    val handoffAccuracy: ReviewRating?,
    @field:NotNull
    val staffInteraction: ReviewRating?,
    @field:NotNull
    val riderRespect: ReviewRating?,
    @field:Schema(maxLength = 200, nullable = true)
    val comment: String? = null,
) {
    @get:AssertTrue(message = "의견은 trim 후 200자 이하여야 합니다.")
    @get:Schema(hidden = true)
    val commentLengthValid: Boolean
        get() = comment?.trim()?.length?.let { it <= 200 } ?: true
}

data class UpdateReviewRequest(
    @field:NotNull
    val pickupSpaceCleanliness: ReviewRating?,
    @field:NotNull
    val packagingStability: ReviewRating?,
    @field:NotNull
    val orderReadiness: ReviewRating?,
    @field:NotNull
    val handoffAccuracy: ReviewRating?,
    @field:NotNull
    val staffInteraction: ReviewRating?,
    @field:NotNull
    val riderRespect: ReviewRating?,
    @field:Schema(maxLength = 200, nullable = true)
    val comment: String? = null,
) {
    @get:AssertTrue(message = "의견은 trim 후 200자 이하여야 합니다.")
    @get:Schema(hidden = true)
    val commentLengthValid: Boolean
        get() = comment?.trim()?.length?.let { it <= 200 } ?: true
}

data class MyReviewsRequest(
    @field:Schema(description = "createdAt과 reviewId 기반 opaque cursor", nullable = true)
    val cursor: String? = null,
    @field:Min(1)
    @field:Max(50)
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "50")
    val size: Int = 20,
)

enum class RestaurantTargetRequestType {
    EXISTING,
    KAKAO,
    MANUAL_EXISTING_LOCATION,
    MANUAL_ADDRESS,
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ExistingRestaurantTargetRequest::class, name = "EXISTING"),
    JsonSubTypes.Type(value = KakaoRestaurantTargetRequest::class, name = "KAKAO"),
    JsonSubTypes.Type(
        value = ManualExistingLocationRestaurantTargetRequest::class,
        name = "MANUAL_EXISTING_LOCATION",
    ),
    JsonSubTypes.Type(value = ManualAddressRestaurantTargetRequest::class, name = "MANUAL_ADDRESS"),
)
@Schema(
    discriminatorProperty = "type",
    oneOf = [
        ExistingRestaurantTargetRequest::class,
        KakaoRestaurantTargetRequest::class,
        ManualExistingLocationRestaurantTargetRequest::class,
        ManualAddressRestaurantTargetRequest::class,
    ],
    discriminatorMapping = [
        DiscriminatorMapping(value = "EXISTING", schema = ExistingRestaurantTargetRequest::class),
        DiscriminatorMapping(value = "KAKAO", schema = KakaoRestaurantTargetRequest::class),
        DiscriminatorMapping(
            value = "MANUAL_EXISTING_LOCATION",
            schema = ManualExistingLocationRestaurantTargetRequest::class,
        ),
        DiscriminatorMapping(value = "MANUAL_ADDRESS", schema = ManualAddressRestaurantTargetRequest::class),
    ],
)
sealed interface RestaurantTargetRequest {
    val type: RestaurantTargetRequestType
}

data class ExistingRestaurantTargetRequest(
    override val type: RestaurantTargetRequestType,
    @field:Positive
    val restaurantId: Long,
) : RestaurantTargetRequest

data class KakaoRestaurantTargetRequest(
    override val type: RestaurantTargetRequestType,
    @field:NotBlank
    @field:Schema(minLength = 2, maxLength = 100)
    val query: String,
    @field:NotBlank
    @field:Size(max = 255)
    val kakaoPlaceId: String,
) : RestaurantTargetRequest {
    @get:AssertTrue(message = "정규화한 검색어는 2~100자여야 합니다.")
    @get:Schema(hidden = true)
    val normalizedQueryLengthValid: Boolean
        get() = RestaurantNormalization.displayText(query).length in 2..100
}

data class ManualExistingLocationRestaurantTargetRequest(
    override val type: RestaurantTargetRequestType,
    @field:Positive
    val pickupLocationId: Long,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotNull
    val platforms: Set<DeliveryPlatform>?,
) : RestaurantTargetRequest

data class ManualAddressRestaurantTargetRequest(
    override val type: RestaurantTargetRequestType,
    @field:NotBlank
    @field:Schema(minLength = 2, maxLength = 100)
    val addressQuery: String,
    @field:NotBlank
    @field:Size(max = 255)
    val selectedStandardAddress: String,
    @field:Size(max = 255)
    val detailAddress: String? = null,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotNull
    val platforms: Set<DeliveryPlatform>?,
) : RestaurantTargetRequest {
    @get:AssertTrue(message = "정규화한 주소 검색어는 2~100자여야 합니다.")
    @get:Schema(hidden = true)
    val normalizedAddressQueryLengthValid: Boolean
        get() = RestaurantNormalization.displayText(addressQuery).length in 2..100
}
