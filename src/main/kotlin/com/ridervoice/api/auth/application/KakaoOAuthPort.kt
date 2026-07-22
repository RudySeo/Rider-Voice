package com.ridervoice.api.auth.application

data class OAuthAccessToken(val value: String)

data class KakaoUserProfile(val providerSubject: String, val nickname: String?)

interface KakaoOAuthPort {
    fun authorizationUri(state: String): String
    fun exchangeCode(code: String): OAuthAccessToken
    fun getUser(accessToken: OAuthAccessToken): KakaoUserProfile
}
