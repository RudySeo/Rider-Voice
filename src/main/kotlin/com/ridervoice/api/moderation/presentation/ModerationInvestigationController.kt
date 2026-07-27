package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.moderation.application.port.`in`.*
import com.ridervoice.api.moderation.presentation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Moderation Investigation", description = "관리자 조사와 감사 조회 API")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
class AdminInvestigationController(
    private val reviewDetail: GetAdminReviewDetailUseCase,
    private val restaurantSearch: SearchAdminRestaurantsUseCase,
    private val restaurantDetail: GetAdminRestaurantDetailUseCase,
    private val audits: ListModerationAuditsUseCase,
    private val mapper: ModerationInvestigationHttpMapper,
) {
    @Operation(summary = "관리자 리뷰 조사 상세")
    @GetMapping("/reviews/{reviewId}")
    fun review(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reviewId: Long,
    ): AdminReviewDetailResponse = mapper.toResponse(reviewDetail.get(GetAdminReviewDetailQuery(principal.userId, reviewId)))

    @Operation(summary = "관리자 음식점 검색")
    @GetMapping("/restaurants/search")
    fun searchRestaurants(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: AdminRestaurantSearchRequest,
    ): AdminRestaurantSearchPageResponse = mapper.toResponse(restaurantSearch.search(mapper.toSearchQuery(principal.userId, request)))

    @Operation(summary = "관리자 음식점 상세")
    @GetMapping("/restaurants/{restaurantId}")
    fun restaurant(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
    ): AdminRestaurantDetailResponse = mapper.toResponse(restaurantDetail.get(GetAdminRestaurantDetailQuery(principal.userId, restaurantId)))

    @Operation(summary = "관리자 감사 이력")
    @GetMapping("/moderation-audits")
    fun audits(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: ModerationAuditSearchRequest,
    ): ModerationAuditPageResponse = mapper.toResponse(audits.list(mapper.toAuditQuery(principal.userId, request)))
}
