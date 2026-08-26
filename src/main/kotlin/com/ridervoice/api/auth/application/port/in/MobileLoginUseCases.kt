package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.domain.OAuthProvider

fun interface PrepareMobileLoginUseCase {
    fun prepareMobileLogin(command: CompleteSocialLoginCommand): MobileLoginGrantResult
}

fun interface ExchangeMobileLoginUseCase {
    fun exchangeMobileLogin(code: String): com.ridervoice.api.auth.application.AuthTokens
}

data class CompleteSocialLoginCommand(
    val provider: OAuthProvider,
    val providerSubject: String,
) {
    init {
        require(providerSubject.isNotBlank()) { "Provider subject must not be blank" }
    }
}

data class MobileLoginGrantResult(val exchangeCode: String)
