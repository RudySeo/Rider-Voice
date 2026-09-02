package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.RotateRiderInviteCodeCommand
import com.ridervoice.api.auth.application.port.`in`.RotateRiderInviteCodeUseCase
import com.ridervoice.api.auth.presentation.dto.RiderInviteCodeRotationRequest
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/rider-invite-code")
@Tag(name = "Admin Rider Access", description = "라이더 권한 인증번호 관리 API")
class AdminRiderAccessController(
    private val rotateRiderInviteCode: RotateRiderInviteCodeUseCase,
) {
    @Operation(
        summary = "라이더 권한 인증번호 교체",
        description = "현재 공유 인증번호를 폐기하고 새 번호의 BCrypt hash만 저장합니다.",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rotate(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @RequestBody request: RiderInviteCodeRotationRequest,
    ) {
        rotateRiderInviteCode.rotate(RotateRiderInviteCodeCommand(principal.userId, request.code))
    }
}
