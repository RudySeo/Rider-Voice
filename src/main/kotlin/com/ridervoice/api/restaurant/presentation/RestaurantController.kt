package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantUseCase
import com.ridervoice.api.restaurant.presentation.dto.CreateRestaurantRequest
import com.ridervoice.api.restaurant.presentation.dto.RestaurantRegistrationResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchRequest
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants", description = "카카오 장소 기반 음식점 검색과 등록 API")
class RestaurantController(
    private val restaurantUseCase: RestaurantUseCase,
) {

    @Operation(
        summary = "음식점 검색",
        description = "내부 음식점과 카카오 장소 검색 결과를 병합하고 내부 등록 여부를 반환합니다.",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "검색 성공"),
            ApiResponse(
                responseCode = "400",
                description = "검색어 검증 실패",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
        ],
    )
    @GetMapping("/search")
    fun search(
        @Valid @ParameterObject request: RestaurantSearchRequest,
    ): RestaurantSearchResponse = restaurantUseCase.search(request.toQuery()).toResponse()

    @Operation(
        summary = "선택한 카카오 장소 등록",
        description = "원래 검색어로 카카오 검색을 반복해 선택한 장소를 검증하고 내부 음식점으로 멱등 등록합니다.",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 또는 기존 음식점 조회 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 본문 검증 실패",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
            ApiResponse(
                responseCode = "404",
                description = "원래 검색 결과에서 선택 장소를 확인할 수 없음",
                content = [Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                )],
            ),
        ],
    )
    @PostMapping
    fun register(
        @Valid @RequestBody request: CreateRestaurantRequest,
    ): RestaurantRegistrationResponse = restaurantUseCase.register(request.toCommand()).toResponse()
}
