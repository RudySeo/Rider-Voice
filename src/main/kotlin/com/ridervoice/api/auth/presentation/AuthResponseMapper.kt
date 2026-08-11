package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.presentation.dto.AuthTokensResponse
import com.ridervoice.api.auth.presentation.dto.UserResponse
import org.springframework.stereotype.Component

@Component
class AuthResponseMapper {
    fun toAuthTokensResponse(result: CompleteSocialLoginResult) = AuthTokensResponse(
        accessToken = result.accessToken,
        refreshToken = result.refreshToken,
        user = toUserResponse(result.user),
    )

    fun toAuthTokensResponse(tokens: AuthTokens) = AuthTokensResponse(
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        user = toUserResponse(tokens.user),
    )

    fun toUserResponse(user: UserSummary) = UserResponse(
        id = user.id,
        status = user.status,
        role = user.role,
        termsVersion = user.termsVersion,
    )
}
