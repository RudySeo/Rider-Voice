package com.ridervoice.api.auth.presentation.dto

import com.ridervoice.api.auth.domain.UserRole
import io.swagger.v3.oas.annotations.media.Schema

data class OAuth2LoginResponse(
    val termsAgreed: Boolean,
    @field:Schema(
        description = "약관 미동의 사용자에게만 발급되는 onboarding token",
        nullable = true,
    )
    val onboardingToken: String?,
    @field:Schema(
        description = "약관에 동의한 ACTIVE 사용자에게만 발급되는 서비스 token",
        nullable = true,
    )
    val tokens: ServiceTokensResponse?,
)

data class ServiceTokensResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
)

data class UserResponse(
    @field:Schema(format = "int64")
    val id: Long,
    val status: String,
    val role: UserRole,
    @field:Schema(nullable = true)
    val termsVersion: String?,
)
