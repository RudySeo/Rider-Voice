package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationUseCase
import com.ridervoice.api.moderation.presentation.dto.CreateRestaurantInfoReportRequest
import com.ridervoice.api.moderation.presentation.dto.CreateReviewReportRequest
import com.ridervoice.api.moderation.presentation.dto.ModerationPageRequest
import com.ridervoice.api.moderation.presentation.dto.MergeRestaurantRequest
import com.ridervoice.api.moderation.presentation.dto.PendingRestaurantInfoReportPageResponse
import com.ridervoice.api.moderation.presentation.dto.PendingReviewReportPageResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantInfoReportDecisionRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantInfoReportResponse
import com.ridervoice.api.moderation.presentation.dto.RelinkRestaurantPickupLocationRequest
import com.ridervoice.api.moderation.presentation.dto.RenameRestaurantRequest
import com.ridervoice.api.moderation.presentation.dto.ChangeRestaurantStatusRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantRenameResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantStatusChangeResponse
import com.ridervoice.api.moderation.presentation.dto.RelinkRestaurantVerifiedAddressRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantMergeResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantPickupRelinkResponse
import com.ridervoice.api.moderation.presentation.dto.ReviewReportDecisionRequest
import com.ridervoice.api.moderation.presentation.dto.ReviewReportResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@Tag(name = "Reports", description = "사용자 신고 API")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@ApiResponses(
    ApiResponse(
        responseCode = "400",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "401",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "403",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "404",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "409",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "429",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
)
class ModerationReportController(
    private val createReviewReport: CreateReviewReportUseCase,
    private val createRestaurantReport: CreateRestaurantInfoReportUseCase,
    private val mapper: ModerationHttpMapper,
) {
    @Operation(summary = "리뷰 신고")
    @ApiResponse(responseCode = "201", description = "리뷰 신고 접수 완료")
    @PostMapping("/api/v1/reviews/{reviewId}/reports")
    fun reportReview(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reviewId: Long,
        @Valid @RequestBody request: CreateReviewReportRequest,
    ): ResponseEntity<ReviewReportResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        mapper.toResponse(
            createReviewReport.createReviewReport(
                mapper.toCreateReviewReportCommand(principal.userId, reviewId, request),
            ),
        ),
    )

    @Operation(summary = "음식점 정보 신고")
    @ApiResponse(responseCode = "201", description = "음식점 정보 신고 접수 완료")
    @PostMapping("/api/v1/restaurants/{restaurantId}/reports")
    fun reportRestaurant(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: CreateRestaurantInfoReportRequest,
    ): ResponseEntity<RestaurantInfoReportResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        mapper.toResponse(
            createRestaurantReport.createRestaurantInfoReport(
                mapper.toCreateRestaurantReportCommand(principal.userId, restaurantId, request),
            ),
        ),
    )
}

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Moderation Admin", description = "관리자 신고 처리 API")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@ApiResponses(
    ApiResponse(
        responseCode = "400",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "401",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "403",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "404",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "409",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
)
class AdminModerationController(
    private val listReviewReports: ListPendingReviewReportsUseCase,
    private val decideReviewReport: DecideReviewReportUseCase,
    private val listRestaurantReports: ListPendingRestaurantInfoReportsUseCase,
    private val decideRestaurantReport: DecideRestaurantInfoReportUseCase,
    private val mapper: ModerationHttpMapper,
) {
    @Operation(summary = "처리 대기 리뷰 신고 목록")
    @ApiResponse(responseCode = "200", description = "처리 대기 리뷰 신고 목록 조회 성공")
    @GetMapping("/review-reports")
    fun listReviewReports(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: ModerationPageRequest,
    ): PendingReviewReportPageResponse = mapper.toResponse(
        listReviewReports.list(mapper.toReviewReportQuery(principal.userId, request)),
    )

    @Operation(summary = "리뷰 신고 결정")
    @ApiResponse(responseCode = "200", description = "리뷰 신고 결정 완료")
    @PatchMapping("/review-reports/{reportId}")
    fun decideReviewReport(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reportId: Long,
        @Valid @RequestBody request: ReviewReportDecisionRequest,
    ): ReviewReportResponse = mapper.toResponse(
        decideReviewReport.decideReviewReport(
            mapper.toReviewReportDecisionCommand(principal.userId, reportId, request),
        ),
    )

    @Operation(summary = "처리 대기 음식점 정보 신고 목록")
    @ApiResponse(responseCode = "200", description = "처리 대기 음식점 정보 신고 목록 조회 성공")
    @GetMapping("/restaurant-reports")
    fun listRestaurantReports(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: ModerationPageRequest,
    ): PendingRestaurantInfoReportPageResponse = mapper.toResponse(
        listRestaurantReports.list(mapper.toRestaurantReportQuery(principal.userId, request)),
    )

    @Operation(summary = "음식점 정보 신고 결정")
    @ApiResponse(responseCode = "200", description = "음식점 정보 신고 결정 완료")
    @PatchMapping("/restaurant-reports/{reportId}")
    fun decideRestaurantReport(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reportId: Long,
        @Valid @RequestBody request: RestaurantInfoReportDecisionRequest,
    ): RestaurantInfoReportResponse = mapper.toResponse(
        decideRestaurantReport.decideRestaurantInfoReport(
            mapper.toRestaurantReportDecisionCommand(principal.userId, reportId, request),
        ),
    )
}

@Validated
@RestController
@RequestMapping("/api/v1/admin/restaurants")
@Tag(name = "Restaurant Admin", description = "관리자 음식점 병합과 픽업 장소 정정 API")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@ApiResponses(
    ApiResponse(
        responseCode = "400",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "401",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "403",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "404",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
    ApiResponse(
        responseCode = "409",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    ),
)
class AdminRestaurantController(
    private val mergeRestaurant: MergeRestaurantUseCase,
    private val relinkRestaurant: RelinkRestaurantPickupLocationUseCase,
    private val renameRestaurant: RenameRestaurantUseCase,
    private val changeRestaurantStatus: ChangeRestaurantStatusUseCase,
    private val relinkVerifiedAddress: RelinkRestaurantVerifiedAddressUseCase,
    private val mapper: RestaurantAdministrationHttpMapper,
) {
    @Operation(summary = "중복 음식점을 canonical 음식점으로 병합")
    @ApiResponse(responseCode = "200", description = "음식점 병합 완료")
    @PostMapping("/{restaurantId}/merge")
    fun merge(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: MergeRestaurantRequest,
    ): RestaurantMergeResponse = mapper.toResponse(
        mergeRestaurant.merge(mapper.toMergeCommand(principal.userId, restaurantId, request)),
    )

    @Operation(summary = "음식점 픽업 장소 재연결")
    @ApiResponse(responseCode = "200", description = "픽업 장소 재연결 완료")
    @PatchMapping("/{restaurantId}/pickup-location")
    fun relinkPickupLocation(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: RelinkRestaurantPickupLocationRequest,
    ): RestaurantPickupRelinkResponse = mapper.toResponse(
        relinkRestaurant.relinkPickupLocation(
            mapper.toRelinkCommand(principal.userId, restaurantId, request),
        ),
    )

    @Operation(summary = "음식점 이름 정정")
    @ApiResponse(
        responseCode = "200",
        description = "음식점 이름 정정 완료",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RestaurantRenameResponse::class))],
    )
    @PatchMapping("/{restaurantId}/name")
    fun rename(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: RenameRestaurantRequest,
    ): RestaurantRenameResponse = mapper.toResponse(
        renameRestaurant.rename(mapper.toRenameCommand(principal.userId, restaurantId, request)),
    )

    @Operation(summary = "음식점 폐업 또는 재개장 처리")
    @ApiResponse(
        responseCode = "200",
        description = "음식점 상태 변경 완료",
        content = [
            Content(mediaType = "application/json", schema = Schema(implementation = RestaurantStatusChangeResponse::class)),
        ],
    )
    @PatchMapping("/{restaurantId}/status")
    fun changeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: ChangeRestaurantStatusRequest,
    ): RestaurantStatusChangeResponse = mapper.toResponse(
        changeRestaurantStatus.changeStatus(mapper.toStatusCommand(principal.userId, restaurantId, request)),
    )

    @Operation(summary = "검증된 신규 주소로 픽업 장소 재연결")
    @ApiResponse(
        responseCode = "200",
        description = "검증된 신규 주소로 픽업 장소 재연결 완료",
        content = [
            Content(mediaType = "application/json", schema = Schema(implementation = RestaurantPickupRelinkResponse::class)),
        ],
    )
    @PatchMapping("/{restaurantId}/pickup-location/verified-address")
    fun relinkVerifiedAddress(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive restaurantId: Long,
        @Valid @RequestBody request: RelinkRestaurantVerifiedAddressRequest,
    ): RestaurantPickupRelinkResponse = mapper.toResponse(
        relinkVerifiedAddress.relinkVerifiedAddress(
            mapper.toVerifiedRelinkCommand(principal.userId, restaurantId, request),
        ),
    )
}
