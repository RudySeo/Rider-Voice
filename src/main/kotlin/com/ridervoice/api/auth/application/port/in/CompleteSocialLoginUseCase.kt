package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.domain.OAuthProvider

fun interface CompleteProviderLoginUseCase {
    fun complete(command: CompleteSocialLoginCommand): ProviderLoginResult
}

data class CompleteSocialLoginCommand(
    val provider: OAuthProvider,
    val providerSubject: String,
) {
    init {
        require(providerSubject.isNotBlank()) { "Provider subject must not be blank" }
    }
}

data class ProviderLoginResult(
    val refreshToken: String,
)
