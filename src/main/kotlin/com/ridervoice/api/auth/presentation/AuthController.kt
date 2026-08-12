package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.presentation.dto.AccessSessionResponse
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "로그인과 서비스 세션 API")
class AuthController(
    private val refreshSession: RefreshSessionUseCase,
    private val logout: LogoutUseCase,
    private val responseMapper: AuthResponseMapper,
    private val cookies: AuthCookieManager,
) {
    @Operation(
        summary = "서비스 access token 갱신",
        security = [SecurityRequirement(name = OpenApiConfiguration.REFRESH_COOKIE_AUTH)],
    )
    @PostMapping("/refresh")
    fun refresh(
        @Parameter(hidden = true)
        @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): AccessSessionResponse {
        val token = refreshToken?.takeIf(String::isNotBlank) ?: run {
            cookies.clearRefreshToken(response)
            throw AuthenticationRequiredException()
        }
        return try {
            val session = refreshSession.refresh(RefreshSessionCommand(token))
            cookies.writeRefreshToken(response, session.refreshToken)
            responseMapper.toAccessSessionResponse(session)
        } catch (exception: AuthenticationRequiredException) {
            cookies.clearRefreshToken(response)
            throw exception
        }
    }

    @Operation(
        summary = "서비스 로그아웃",
        security = [SecurityRequirement(name = OpenApiConfiguration.REFRESH_COOKIE_AUTH)],
    )
    @ApiResponse(responseCode = "204", description = "로그아웃 완료")
    @PostMapping("/logout")
    fun logout(
        @Parameter(hidden = true)
        @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        try {
            refreshToken?.takeIf(String::isNotBlank)?.let { logout.logout(LogoutCommand(it)) }
        } finally {
            cookies.clearRefreshToken(response)
        }
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
