package com.ridervoice.api.auth.infrastructure.oauth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("ridervoice.auth.oauth2.kakao")
data class KakaoOAuth2Properties(
    val clientId: String = "",
    val clientSecret: String? = null,
    val redirectUri: URI = URI.create(DEFAULT_REDIRECT_URI),
    val authorizationUri: URI = URI.create(DEFAULT_AUTHORIZATION_URI),
    val tokenUri: URI = URI.create(DEFAULT_TOKEN_URI),
    val userInfoUri: URI = URI.create(DEFAULT_USER_INFO_URI),
) {
    companion object {
        const val DEFAULT_REDIRECT_URI = "http://localhost:8080/api/v1/auth/oauth2/callback/kakao"
        const val DEFAULT_AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
        const val DEFAULT_TOKEN_URI = "https://kauth.kakao.com/oauth/token"
        const val DEFAULT_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me"
    }
}
