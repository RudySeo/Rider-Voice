package com.ridervoice.api.auth.presentation.dto

import com.ridervoice.api.auth.domain.UserRole
import io.swagger.v3.oas.annotations.media.Schema

data class AccessSessionResponse(
    val accessToken: String,
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
