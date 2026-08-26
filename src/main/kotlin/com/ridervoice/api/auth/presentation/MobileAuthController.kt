package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.ExchangeMobileLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.presentation.dto.MobileExchangeRequest
import com.ridervoice.api.auth.presentation.dto.MobileLogoutRequest
import com.ridervoice.api.auth.presentation.dto.MobileRefreshRequest
import com.ridervoice.api.auth.presentation.dto.MobileSessionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/mobile")
@Tag(name = "Mobile authentication", description = "네이티브 앱 로그인과 token 회전 API")
class MobileAuthController(
    private val exchangeMobileLogin: ExchangeMobileLoginUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val logout: LogoutUseCase,
    private val responseMapper: AuthResponseMapper,
) {
    @Operation(summary = "일회용 모바일 로그인 코드 교환")
    @PostMapping("/exchange")
    fun exchange(@Valid @RequestBody request: MobileExchangeRequest): MobileSessionResponse =
        responseMapper.toMobileSessionResponse(exchangeMobileLogin.exchangeMobileLogin(request.code))

    @Operation(summary = "모바일 refresh token 회전")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: MobileRefreshRequest): MobileSessionResponse =
        responseMapper.toMobileSessionResponse(refreshSession.refresh(RefreshSessionCommand(request.refreshToken)))

    @Operation(summary = "모바일 로그아웃")
    @ApiResponse(responseCode = "204", description = "로그아웃 완료")
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: MobileLogoutRequest): ResponseEntity<Void> {
        logout.logout(LogoutCommand(request.refreshToken))
        return ResponseEntity.noContent().build()
    }
}
