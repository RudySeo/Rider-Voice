package com.ridervoice.api.restaurant.presentation.dto

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class RestaurantSearchRequest(
    @field:NotBlank
    @field:Parameter(
        description = "음식점명 또는 주소 검색어",
        required = true,
        example = "강남 분식",
        schema = Schema(minLength = 1),
    )
    val query: String = "",
)

@Schema(description = "선택한 카카오 장소의 내부 음식점 등록 요청")
data class CreateRestaurantRequest(
    @field:NotBlank
    @field:Schema(
        description = "장소 선택에 사용한 원래 검색어",
        example = "강남 분식",
        minLength = 1,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val query: String,

    @field:NotBlank
    @field:Schema(
        description = "검색 결과에서 선택한 카카오 장소 ID",
        example = "1234567890",
        minLength = 1,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val kakaoPlaceId: String,
)
