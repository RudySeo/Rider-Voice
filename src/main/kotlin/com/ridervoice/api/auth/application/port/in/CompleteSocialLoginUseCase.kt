package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.domain.OAuthProvider

fun interface CompleteProviderLoginUseCase {
    fun complete(command: CompleteSocialLoginCommand): ProviderLoginResult
}

fun interface ExchangeSocialLoginCodeUseCase {
    fun exchange(command: ExchangeSocialLoginCodeCommand): CompleteSocialLoginResult
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
    val code: String,
)

data class ExchangeSocialLoginCodeCommand(
    val code: String,
) {
    init {
        require(code.isNotBlank()) { "OAuth exchange code must not be blank" }
    }
}

data class CompleteSocialLoginResult(
    val user: UserSummary,
    val accessToken: String,
    val refreshToken: String,
)
