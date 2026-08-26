package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "현재 사용자 API")
class UserController(
    private val getCurrentUser: GetCurrentUserUseCase,
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
}
