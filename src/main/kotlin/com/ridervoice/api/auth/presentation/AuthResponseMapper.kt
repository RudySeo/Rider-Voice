package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.presentation.dto.AuthTokensResponse
import com.ridervoice.api.auth.presentation.dto.OAuth2LoginResponse
import com.ridervoice.api.auth.presentation.dto.ServiceTokensResponse
import com.ridervoice.api.auth.presentation.dto.UserResponse
import org.springframework.stereotype.Component

@Component
class AuthResponseMapper {
    fun toOAuth2LoginResponse(result: CompleteSocialLoginResult) = OAuth2LoginResponse(
        termsAgreed = result.termsAgreed,
        onboardingToken = result.onboardingToken,
        tokens = result.tokens?.let { ServiceTokensResponse(it.accessToken, it.refreshToken) },
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
