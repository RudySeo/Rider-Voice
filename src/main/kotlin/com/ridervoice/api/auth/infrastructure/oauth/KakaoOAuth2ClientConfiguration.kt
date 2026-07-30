package com.ridervoice.api.auth.infrastructure.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoOAuth2Properties::class)
class KakaoOAuth2ClientConfiguration {

    @Bean
    fun kakaoClientRegistrationFactory(properties: KakaoOAuth2Properties) =
        KakaoClientRegistrationFactory(properties)

    @Bean
    fun clientRegistrationRepository(
        factory: KakaoClientRegistrationFactory,
    ): ClientRegistrationRepository = InMemoryClientRegistrationRepository(factory.create())

    @Bean
    fun kakaoOAuth2UserService() = KakaoOAuth2UserService()
}

class KakaoClientRegistrationFactory(
    private val properties: KakaoOAuth2Properties,
) {
    fun create(): ClientRegistration {
        val clientId = properties.clientId.trim()
        require(clientId.isNotEmpty()) { "Kakao OAuth client ID must be configured" }

        val clientSecret = properties.clientSecret?.trim()?.takeIf(String::isNotEmpty)
        val authenticationMethod = if (clientSecret == null) {
            ClientAuthenticationMethod.NONE
        } else {
            ClientAuthenticationMethod.CLIENT_SECRET_POST
        }

        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(clientId)
            .apply {
                if (clientSecret != null) {
                    clientSecret(clientSecret)
                }
            }
            .clientAuthenticationMethod(authenticationMethod)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(properties.redirectUri.toString())
            .authorizationUri(properties.authorizationUri.toString())
            .tokenUri(properties.tokenUri.toString())
            .userInfoUri(properties.userInfoUri.toString())
            .userNameAttributeName(USER_NAME_ATTRIBUTE)
            .clientName(CLIENT_NAME)
            .build()
    }

    private companion object {
        const val REGISTRATION_ID = "kakao"
        const val USER_NAME_ATTRIBUTE = "id"
        const val CLIENT_NAME = "Kakao"
    }
}
