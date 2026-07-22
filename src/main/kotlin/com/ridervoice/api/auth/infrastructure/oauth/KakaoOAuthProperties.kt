package com.ridervoice.api.auth.infrastructure.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kakao.oauth")
data class KakaoOAuthProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "http://localhost:8080/api/v1/auth/kakao/callback",
    val authorizationUri: String = "https://kauth.kakao.com/oauth/authorize",
    val tokenUri: String = "https://kauth.kakao.com/oauth/token",
    val userUri: String = "https://kapi.kakao.com/v2/user/me"
)
