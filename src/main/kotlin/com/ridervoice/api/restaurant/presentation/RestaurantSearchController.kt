package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import com.ridervoice.api.restaurant.presentation.dto.AddressSearchResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchResponse
import com.ridervoice.api.restaurant.presentation.dto.SearchQueryRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants", description = "공개 음식점 검색 API")
class RestaurantSearchController(
    private val searchRestaurants: SearchRestaurantsUseCase,
    private val mapper: RestaurantSearchHttpMapper,
) {
    @Operation(
        summary = "음식점 검색",
        description = "내부 배달 브랜드와 카카오 장소 후보를 최대 20개까지 병합해 반환합니다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = RestaurantSearchResponse::class),
        )],
    )
    @ApiResponse(
        responseCode = "429",
        description = "호출자당 분당 30회 공개 검색 제한 초과",
        content = [Content(
            mediaType = "application/problem+json",
            schema = Schema(implementation = ProblemDetail::class),
        )],
    )
    @GetMapping("/search")
    fun search(
        @Valid @ParameterObject request: SearchQueryRequest,
    ): RestaurantSearchResponse = mapper.toRestaurantSearchResponse(
        searchRestaurants.search(mapper.toRestaurantSearchCommand(request)),
    )
}

@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Addresses", description = "리뷰 작성용 주소 검색 API")
class AddressSearchController(
    private val searchAddresses: SearchAddressesUseCase,
    private val mapper: RestaurantSearchHttpMapper,
) {
    @Operation(
        summary = "주소 검색",
        description = "원 검색어와 표준 주소 후보 및 기존 픽업 장소 식별자를 최대 20개까지 반환합니다.",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @GetMapping("/search")
    fun search(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: SearchQueryRequest,
    ): AddressSearchResponse = mapper.toAddressSearchResponse(
        searchAddresses.search(mapper.toAddressSearchCommand(principal.userId, request)),
    )
}
