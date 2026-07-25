package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.domain.OAuthProvider

fun interface CompleteSocialLoginUseCase {
    fun complete(command: CompleteSocialLoginCommand): CompleteSocialLoginResult
}

data class CompleteSocialLoginCommand(
    val provider: OAuthProvider,
    val providerSubject: String,
) {
    init {
        require(providerSubject.isNotBlank()) { "Provider subject must not be blank" }
    }
}

data class CompleteSocialLoginResult(
    val user: UserSummary,
    val termsAgreed: Boolean,
    val onboardingToken: String?,
    val tokens: ServiceTokens?,
)

data class ServiceTokens(
    val accessToken: String,
    val refreshToken: String,
)
