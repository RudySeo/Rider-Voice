package com.ridervoice.api.auth.infrastructure.oauth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import java.util.UUID

class KakaoOAuth2ClientConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(KakaoOAuth2ClientConfiguration::class.java)

    @Test
    fun `client registration uses public client authentication when secret is absent`() {
        val clientId = UUID.randomUUID().toString()

        contextRunner
            .withPropertyValues("ridervoice.auth.oauth2.kakao.client-id=$clientId")
            .run { context ->
                val registration = context.getBean(KakaoClientRegistrationFactory::class.java).create()

                assertThat(registration.registrationId).isEqualTo("kakao")
                assertThat(registration.clientId).isEqualTo(clientId)
                assertThat(registration.clientSecret).isEmpty()
                assertThat(registration.clientAuthenticationMethod).isEqualTo(ClientAuthenticationMethod.NONE)
                assertThat(registration.redirectUri)
                    .isEqualTo("http://localhost:8080/api/v1/auth/oauth2/callback/kakao")
                assertThat(registration.providerDetails.authorizationUri)
                    .isEqualTo("https://kauth.kakao.com/oauth/authorize")
                assertThat(registration.providerDetails.tokenUri)
                    .isEqualTo("https://kauth.kakao.com/oauth/token")
                assertThat(registration.providerDetails.userInfoEndpoint.uri)
                    .isEqualTo("https://kapi.kakao.com/v2/user/me")
                assertThat(registration.providerDetails.userInfoEndpoint.userNameAttributeName).isEqualTo("id")
            }
    }

    @Test
    fun `client registration uses client secret post when secret is configured`() {
        val clientId = UUID.randomUUID().toString()
        val clientSecret = UUID.randomUUID().toString()

        contextRunner
            .withPropertyValues(
                "ridervoice.auth.oauth2.kakao.client-id=$clientId",
                "ridervoice.auth.oauth2.kakao.client-secret=$clientSecret",
            )
            .run { context ->
                val registration = context.getBean(KakaoClientRegistrationFactory::class.java).create()

                assertThat(registration.clientSecret).isEqualTo(clientSecret)
                assertThat(registration.clientAuthenticationMethod)
                    .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            }
    }
}
