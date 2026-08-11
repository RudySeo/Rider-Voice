package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeCommand
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeUseCase
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.presentation.dto.AuthTokensResponse
import com.ridervoice.api.auth.presentation.dto.OAuthExchangeCodeRequest
import com.ridervoice.api.auth.presentation.dto.TokenRequest
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.InvalidOAuthExchangeRequestException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@Tag(name = "Authentication", description = "로그인과 서비스 세션 API")
class OAuthExchangeController(
    private val exchangeSocialLoginCode: ExchangeSocialLoginCodeUseCase,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(summary = "OAuth 단일 사용 코드 교환")
    @PostMapping("/exchange")
    fun exchange(
        @Valid @RequestBody request: OAuthExchangeCodeRequest,
        bindingResult: BindingResult,
    ): AuthTokensResponse {
        if (bindingResult.hasErrors()) {
            throw InvalidOAuthExchangeRequestException()
        }
        return responseMapper.toAuthTokensResponse(
            exchangeSocialLoginCode.exchange(ExchangeSocialLoginCodeCommand(request.code)),
        )
    }
}

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "로그인과 서비스 세션 API")
class AuthController(
    private val refreshSession: RefreshSessionUseCase,
    private val logout: LogoutUseCase,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(summary = "서비스 access token 갱신")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: TokenRequest): AuthTokensResponse =
        responseMapper.toAuthTokensResponse(
            refreshSession.refresh(RefreshSessionCommand(request.refreshToken)),
        )

    @Operation(
        summary = "서비스 로그아웃",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @ApiResponse(responseCode = "204", description = "로그아웃 완료")
    @PostMapping("/logout")
    fun logout(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        @Valid @RequestBody request: TokenRequest,
    ): ResponseEntity<Void> {
        logout.logout(LogoutCommand(principal.userId, request.refreshToken))
        return ResponseEntity.noContent().build()
    }
}

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
