package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.presentation.dto.AccessSessionResponse
import com.ridervoice.api.auth.presentation.dto.UserResponse
import org.springframework.stereotype.Component

@Component
class AuthResponseMapper {
    fun toAccessSessionResponse(tokens: AuthTokens) = AccessSessionResponse(
        accessToken = tokens.accessToken,
        user = toUserResponse(tokens.user),
    )

    fun toUserResponse(user: UserSummary) = UserResponse(
        id = user.id,
        status = user.status,
        role = user.role,
        termsVersion = user.termsVersion,
    )
}
