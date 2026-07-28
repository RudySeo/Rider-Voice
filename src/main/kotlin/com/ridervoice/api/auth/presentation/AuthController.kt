package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthService
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeCommand
import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeUseCase
import com.ridervoice.api.auth.presentation.dto.AuthTokensResponse
import com.ridervoice.api.auth.presentation.dto.ConsentRequest
import com.ridervoice.api.auth.presentation.dto.OAuth2LoginResponse
import com.ridervoice.api.auth.presentation.dto.OAuthExchangeCodeRequest
import com.ridervoice.api.auth.presentation.dto.TokenRequest
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.InvalidOAuthExchangeRequestException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OnboardingPrincipal
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
@Tag(name = "Authentication", description = "서비스 온보딩과 세션 API")
class OAuthExchangeController(
    private val exchangeSocialLoginCode: ExchangeSocialLoginCodeUseCase,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(summary = "OAuth 단일 사용 코드 교환")
    @PostMapping("/exchange")
    fun exchange(
        @Valid @RequestBody request: OAuthExchangeCodeRequest,
        bindingResult: BindingResult,
    ): OAuth2LoginResponse {
        if (bindingResult.hasErrors()) {
            throw InvalidOAuthExchangeRequestException()
        }
        return responseMapper.toOAuth2LoginResponse(
            exchangeSocialLoginCode.exchange(ExchangeSocialLoginCodeCommand(request.code)),
        )
    }
}

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "서비스 온보딩과 세션 API")
class AuthController(
    private val auth: AuthService,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(
        summary = "필수 약관 동의",
        security = [SecurityRequirement(name = OpenApiConfiguration.ONBOARDING_BEARER_AUTH)],
    )
    @PostMapping("/consents")
    fun consent(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: OnboardingPrincipal,
        @Valid @RequestBody request: ConsentRequest,
    ): AuthTokensResponse = responseMapper.toAuthTokensResponse(auth.agree(principal, request.termsVersion))

    @Operation(summary = "서비스 access token 갱신")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: TokenRequest): AuthTokensResponse =
        responseMapper.toAuthTokensResponse(auth.refresh(request.refreshToken))

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
        auth.logout(principal, request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "현재 사용자 API")
class UserController(
    private val auth: AuthService,
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
        responseMapper.toUserResponse(auth.me(principal))
}
