package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderCommand
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderUseCase
import com.ridervoice.api.auth.presentation.dto.RiderVerificationRequest
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "현재 사용자 API")
class UserController(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val verifyRider: VerifyRiderUseCase,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(
        summary = "현재 사용자 조회",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @GetMapping("/me")
    fun me(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
    ) =
        responseMapper.toUserResponse(getCurrentUser.get(GetCurrentUserQuery(principal.userId)))

    @Operation(
        summary = "라이더 권한 인증",
        description = "6자리 공유 인증번호를 확인해 USER를 RIDER로 승격합니다.",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @PostMapping("/me/rider-verification")
    fun verifyRider(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @RequestBody request: RiderVerificationRequest,
    ) = responseMapper.toUserResponse(verifyRider.verify(VerifyRiderCommand(principal.userId, request.code)))
}
