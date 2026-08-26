package com.ridervoice.api.auth.presentation.dto

import jakarta.validation.constraints.NotBlank

data class MobileExchangeRequest(
    @field:NotBlank val code: String,
)

data class MobileRefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class MobileLogoutRequest(
    @field:NotBlank val refreshToken: String,
)
