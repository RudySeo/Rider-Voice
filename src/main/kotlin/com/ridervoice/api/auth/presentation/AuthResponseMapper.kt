package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.presentation.dto.MobileSessionResponse
import com.ridervoice.api.auth.presentation.dto.UserResponse
import org.springframework.stereotype.Component

@Component
class AuthResponseMapper {
    fun toMobileSessionResponse(tokens: AuthTokens) = MobileSessionResponse(
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        user = toUserResponse(tokens.user),
    )

    fun toUserResponse(user: UserSummary) = UserResponse(
        id = user.id,
        status = user.status,
        role = user.role,
    )
}
