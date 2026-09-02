package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.restaurant.application.port.`in`.GetPublicRestaurantDetailUseCase
import com.ridervoice.api.restaurant.presentation.dto.RestaurantDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants", description = "공개 음식점 조회 API")
class RestaurantDetailController(
    private val getRestaurantDetail: GetPublicRestaurantDetailUseCase,
    private val mapper: RestaurantDetailHttpMapper,
) {
    @Operation(
        summary = "음식점 상세 조회",
        description = "배달 브랜드, 픽업 장소와 브랜드·장소 리포트를 반환합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "음식점 상세 조회 성공"),
        ApiResponse(
            responseCode = "404",
            description = "음식점을 찾을 수 없음",
            content = [
                Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = Schema(implementation = ProblemDetail::class),
                ),
            ],
        ),
    )
    @GetMapping("/{restaurantId}")
    fun get(
        @Parameter(schema = Schema(type = "integer", format = "int64"))
        @PathVariable restaurantId: Long,
    ): RestaurantDetailResponse = mapper.toResponse(getRestaurantDetail.get(restaurantId))
}
