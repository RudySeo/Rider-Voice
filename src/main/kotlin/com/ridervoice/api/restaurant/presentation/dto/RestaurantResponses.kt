package com.ridervoice.api.restaurant.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "음식점 검색 결과")
data class RestaurantSearchResponse(
    @field:Schema(description = "내부 음식점과 카카오 장소를 병합한 후보 목록")
    val candidates: List<RestaurantCandidateResponse>,
)

@Schema(description = "음식점 검색 후보")
data class RestaurantCandidateResponse(
    @field:Schema(
        description = "등록된 내부 음식점 ID. 아직 등록되지 않은 카카오 장소이면 null",
        nullable = true,
    )
    val restaurantId: UUID?,

    @field:Schema(description = "카카오 장소 ID", example = "1234567890")
    val kakaoPlaceId: String,

    @field:Schema(description = "카카오 장소명", example = "라이더보이스 강남점")
    val name: String,

    @field:Schema(description = "카카오가 제공한 주소", example = "서울 강남구 테헤란로 1")
    val address: String,

    @field:Schema(description = "위도", example = "37.4987654")
    val latitude: BigDecimal,

    @field:Schema(description = "경도", example = "127.0276543")
    val longitude: BigDecimal,
)

@Schema(description = "내부 음식점 등록 결과")
data class RestaurantRegistrationResponse(
    @field:Schema(description = "내부 음식점 ID")
    val restaurantId: UUID,

    @field:Schema(description = "카카오 장소 ID", example = "1234567890")
    val kakaoPlaceId: String,

    @field:Schema(description = "카카오 장소명", example = "라이더보이스 강남점")
    val name: String,

    @field:Schema(description = "카카오가 제공한 주소", example = "서울 강남구 테헤란로 1")
    val address: String,

    @field:Schema(description = "위도", example = "37.4987654")
    val latitude: BigDecimal,

    @field:Schema(description = "경도", example = "127.0276543")
    val longitude: BigDecimal,
)
