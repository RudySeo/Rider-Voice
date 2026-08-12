package com.ridervoice.api.auth.presentation

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
class OAuth2SecurityConfig(
    private val clientRegistrationRepository: ClientRegistrationRepository,
    private val oauth2UserService: OAuth2UserService<OAuth2UserRequest, OAuth2User>,
    private val successHandler: OAuth2LoginSuccessHandler,
    private val failureHandler: OAuth2LoginFailureHandler,
) {

    @Bean
    @Order(1)
    fun oauth2SecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .securityMatcher(
            "$AUTHORIZATION_BASE_URI/**",
            "$CALLBACK_BASE_URI/**",
        )
        .csrf { it.disable() }
        .requestCache { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .oauth2Login {
            it.authorizationEndpoint { endpoint ->
                endpoint
                    .baseUri(AUTHORIZATION_BASE_URI)
                    .authorizationRequestResolver(authorizationRequestResolver())
                    .authorizationRequestRepository(HttpSessionOAuth2AuthorizationRequestRepository())
            }
            it.redirectionEndpoint { endpoint -> endpoint.baseUri("$CALLBACK_BASE_URI/*") }
            it.userInfoEndpoint { endpoint -> endpoint.userService(oauth2UserService) }
            it.successHandler(successHandler)
            it.failureHandler(failureHandler)
        }
        .build()

    private fun authorizationRequestResolver() = DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository,
        AUTHORIZATION_BASE_URI,
    ).apply {
        setAuthorizationRequestCustomizer { request ->
            request.additionalParameters { parameters ->
                parameters[REAUTHENTICATION_PARAMETER] = REAUTHENTICATION_VALUE
            }
        }
    }

    private companion object {
        const val AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorization"
        const val CALLBACK_BASE_URI = "/api/v1/auth/oauth2/callback"
        const val REAUTHENTICATION_PARAMETER = "prompt"
        const val REAUTHENTICATION_VALUE = "login"
    }
}
