package com.ridervoice.api.review.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.review.application.port.`in`.CreateReviewUseCase
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.presentation.dto.CreateReviewRequest
import com.ridervoice.api.review.presentation.dto.DeleteReviewResponse
import com.ridervoice.api.review.presentation.dto.MyReviewListResponse
import com.ridervoice.api.review.presentation.dto.MyReviewsRequest
import com.ridervoice.api.review.presentation.dto.ReviewResponse
import com.ridervoice.api.review.presentation.dto.UpdateReviewRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews", description = "리뷰 작성자 API")
class ReviewController(
    private val createReview: CreateReviewUseCase,
    private val updateReview: UpdateReviewUseCase,
    private val deleteReview: DeleteReviewUseCase,
    private val mapper: ReviewHttpMapper,
) {
    @Operation(
        summary = "리뷰 작성",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @ApiResponse(responseCode = "201", description = "리뷰 작성 완료")
    @PostMapping
    fun create(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @RequestBody request: CreateReviewRequest,
    ): ResponseEntity<ReviewResponse> {
        val response = mapper.toReviewResponse(createReview.create(mapper.toCreateCommand(principal.userId, request)))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "현재 리뷰 수정",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @PatchMapping("/{reviewId}")
    fun update(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reviewId: Long,
        @Valid @RequestBody request: UpdateReviewRequest,
    ): ReviewResponse = mapper.toReviewResponse(
        updateReview.update(mapper.toUpdateCommand(principal.userId, reviewId, request)),
    )

    @Operation(
        summary = "현재 리뷰 삭제",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @DeleteMapping("/{reviewId}")
    fun delete(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @PathVariable @Positive reviewId: Long,
    ): DeleteReviewResponse = mapper.toDeleteReviewResponse(
        deleteReview.delete(DeleteReviewCommand(principal.userId, reviewId)),
    )
}

@Validated
@RestController
@RequestMapping("/api/v1/users/me/reviews")
@Tag(name = "Reviews", description = "리뷰 작성자 API")
class MyReviewController(
    private val listMyReviews: ListMyReviewsUseCase,
    private val mapper: ReviewHttpMapper,
) {
    @Operation(
        summary = "내 리뷰 목록 조회",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @GetMapping
    fun list(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @ParameterObject request: MyReviewsRequest,
    ): MyReviewListResponse = mapper.toMyReviewListResponse(
        listMyReviews.list(mapper.toListCommand(principal.userId, request)),
    )
}
