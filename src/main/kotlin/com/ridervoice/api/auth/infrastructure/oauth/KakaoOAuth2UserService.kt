package com.ridervoice.api.auth.infrastructure.oauth

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User

class KakaoOAuth2UserService(
    private val delegate: OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val providerUser = try {
            delegate.loadUser(userRequest) ?: throw safeFailure(PROVIDER_FAILURE_CODE)
        } catch (_: RuntimeException) {
            throw safeFailure(PROVIDER_FAILURE_CODE)
        }

        val providerSubject = providerSubject(providerUser.attributes[USER_NAME_ATTRIBUTE])
            ?: throw safeFailure(INVALID_RESPONSE_CODE)

        return DefaultOAuth2User(
            providerUser.authorities,
            mapOf(USER_NAME_ATTRIBUTE to providerSubject),
            USER_NAME_ATTRIBUTE,
        )
    }

    private fun providerSubject(rawId: Any?): String? {
        val id = when (rawId) {
            is Byte, is Short, is Int, is Long -> (rawId as Number).toLong()
            else -> return null
        }
        return id.takeIf { it > 0 }?.toString()
    }

    private fun safeFailure(errorCode: String) = OAuth2AuthenticationException(
        OAuth2Error(errorCode),
        SAFE_FAILURE_MESSAGE,
    )

    private companion object {
        const val USER_NAME_ATTRIBUTE = "id"
        const val PROVIDER_FAILURE_CODE = "kakao_user_info_failed"
        const val INVALID_RESPONSE_CODE = "invalid_kakao_user_info"
        const val SAFE_FAILURE_MESSAGE = "Kakao user information could not be verified"
    }
}
