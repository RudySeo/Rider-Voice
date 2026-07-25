package com.ridervoice.api.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

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
