package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthService
import com.ridervoice.api.common.config.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class AuthorizationUrlResponse(
    @field:Schema(description = "카카오 OAuth 인증 페이지 URL")
    val authorizationUrl: String,
)

data class ConsentRequest(
    @field:NotBlank
    @field:Schema(description = "사용자가 동의한 약관 버전", example = "2026-07-01")
    val termsVersion: String,
)

data class TokenRequest(
    @field:NotBlank
    @field:Schema(description = "서비스 refresh token")
    val refreshToken: String,
)

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "카카오 로그인과 서비스 세션 API")
class AuthController(private val auth: AuthService) {
    @Operation(summary = "카카오 로그인 URL 생성")
    @GetMapping("/kakao/authorize")
    fun authorize() = AuthorizationUrlResponse(auth.authorize())

    @Operation(summary = "카카오 로그인 callback 처리")
    @GetMapping("/kakao/callback")
    fun callback(
        @Parameter(description = "카카오가 발급한 authorization code") @RequestParam code: String,
        @Parameter(description = "로그인 요청 위조 방지 state") @RequestParam state: String,
    ) = auth.callback(code, state)

    @Operation(
        summary = "필수 약관 동의",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @PostMapping("/consents")
    fun consent(
        @RequestHeader("Authorization") authorization: String,
        @Valid @RequestBody request: ConsentRequest,
    ) = auth.agree(auth.userIdFor(authorization.removePrefix("Bearer ")), request.termsVersion)

    @Operation(summary = "서비스 access token 갱신")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: TokenRequest) = auth.refresh(request.refreshToken)

    @Operation(summary = "서비스 로그아웃")
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: TokenRequest): ResponseEntity<Void> {
        auth.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "현재 사용자 API")
class UserController(private val auth: AuthService) {
    @Operation(
        summary = "현재 사용자 조회",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)],
    )
    @GetMapping("/me")
    fun me(@RequestHeader("Authorization") authorization: String) = auth.me(authorization.removePrefix("Bearer "))
}
