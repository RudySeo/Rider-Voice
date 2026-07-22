package com.ridervoice.api.auth.infrastructure.oauth

import com.ridervoice.api.auth.application.KakaoOAuthPort
import com.ridervoice.api.auth.application.KakaoUserProfile
import com.ridervoice.api.auth.application.OAuthAccessToken
import org.springframework.boot.context.properties.EnableConfigurationProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

class OAuthProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
@EnableConfigurationProperties(KakaoOAuthProperties::class)
class KakaoOAuthAdapter(private val properties: KakaoOAuthProperties) : KakaoOAuthPort {
    private val client = RestClient.builder().build()

    override fun authorizationUri(state: String): String = UriComponentsBuilder
        .fromUriString(properties.authorizationUri)
        .queryParam("response_type", "code")
        .queryParam("client_id", properties.clientId)
        .queryParam("redirect_uri", properties.redirectUri)
        .queryParam("state", state)
        .build()
        .toUriString()

    override fun exchangeCode(code: String): OAuthAccessToken = try {
        val response = client.post().uri(properties.tokenUri)
            .headers { it.contentType = MediaType.APPLICATION_FORM_URLENCODED }
            .body("grant_type=authorization_code&client_id=${encode(properties.clientId)}&redirect_uri=${encode(properties.redirectUri)}&code=${encode(code)}" +
                if (properties.clientSecret.isBlank()) "" else "&client_secret=${encode(properties.clientSecret)}")
            .retrieve().body(TokenResponse::class.java)
            ?: throw OAuthProviderException("Kakao token response was empty")
        OAuthAccessToken(response.accessToken)
    } catch (ex: OAuthProviderException) { throw ex } catch (ex: Exception) {
        throw OAuthProviderException("Kakao token exchange failed", ex)
    }

    override fun getUser(accessToken: OAuthAccessToken): KakaoUserProfile = try {
        val response = client.get().uri(properties.userUri)
            .headers { it.set("Authorization", "Bearer ${accessToken.value}") }
            .retrieve().body(UserResponse::class.java)
            ?: throw OAuthProviderException("Kakao user response was empty")
        KakaoUserProfile(response.id.toString(), response.properties?.nickname)
    } catch (ex: OAuthProviderException) { throw ex } catch (ex: Exception) {
        throw OAuthProviderException("Kakao user lookup failed", ex)
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8)
    private data class TokenResponse(@JsonProperty("access_token") val accessToken: String = "")
    private data class UserResponse(val id: Long = 0, val properties: Properties? = null)
    private data class Properties(val nickname: String? = null)
}
