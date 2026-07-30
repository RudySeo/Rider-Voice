package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.CompleteProviderLoginUseCase
import com.ridervoice.api.auth.domain.OAuthProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2LoginSuccessHandler(
    private val completeProviderLogin: CompleteProviderLoginUseCase,
    private val failureHandler: OAuth2LoginFailureHandler,
    @Value("\${ridervoice.auth.frontend-base-url:http://localhost:5173}")
    private val frontendBaseUrl: String,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        try {
            val oauth = authentication as? OAuth2AuthenticationToken
                ?: throw InternalAuthenticationServiceException("Unsupported OAuth authentication")
            val provider = OAuthProvider.entries.firstOrNull {
                it.name.equals(oauth.authorizedClientRegistrationId, ignoreCase = true)
            } ?: throw InternalAuthenticationServiceException("Unsupported OAuth provider")
            val result = completeProviderLogin.complete(
                CompleteSocialLoginCommand(provider, oauth.principal.name),
            )

            destroyTemporarySession(request)
            response.sendRedirect(frontendCallback("code", result.code))
        } catch (_: RuntimeException) {
            failureHandler.onAuthenticationFailure(
                request,
                response,
                InternalAuthenticationServiceException("Social login failed"),
            )
        }
    }

    private fun frontendCallback(name: String, value: String): String = UriComponentsBuilder
        .fromUriString(frontendBaseUrl.trimEnd('/'))
        .path(FRONTEND_CALLBACK_PATH)
        .queryParam(name, value)
        .build()
        .encode()
        .toUriString()
}

@Component
class OAuth2LoginFailureHandler(
    @Value("\${ridervoice.auth.frontend-base-url:http://localhost:5173}")
    private val frontendBaseUrl: String,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: org.springframework.security.core.AuthenticationException,
    ) {
        destroyTemporarySession(request)
        val redirect = UriComponentsBuilder
            .fromUriString(frontendBaseUrl.trimEnd('/'))
            .path(FRONTEND_CALLBACK_PATH)
            .queryParam("error", "oauth_failed")
            .build()
            .encode()
            .toUriString()
        response.sendRedirect(redirect)
    }
}

private const val FRONTEND_CALLBACK_PATH = "/auth/callback"

private fun destroyTemporarySession(request: HttpServletRequest) {
    request.getSession(false)?.invalidate()
    SecurityContextHolder.clearContext()
}
