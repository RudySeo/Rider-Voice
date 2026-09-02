package com.ridervoice.api.auth.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class MobileExchangeRequest(
    @field:NotBlank val code: String,
)

data class MobileRefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class MobileLogoutRequest(
    @field:NotBlank val refreshToken: String,
)

data class RiderVerificationRequest(
    @field:Pattern(regexp = "^[0-9]{6}$")
    val code: String,
)

data class RiderInviteCodeRotationRequest(
    @field:Pattern(regexp = "^[0-9]{6}$")
    val code: String,
)
