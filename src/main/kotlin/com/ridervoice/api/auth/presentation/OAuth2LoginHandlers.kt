package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginUseCase
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.common.security.SecurityProblemHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class OAuth2LoginSuccessHandler(
    private val completeSocialLogin: CompleteSocialLoginUseCase,
    private val objectMapper: ObjectMapper,
    private val failureHandler: OAuth2LoginFailureHandler,
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
            val result = completeSocialLogin.complete(
                CompleteSocialLoginCommand(provider, oauth.principal.name),
            )
            val body = mapOf(
                "termsAgreed" to result.termsAgreed,
                "onboardingToken" to result.onboardingToken,
                "tokens" to result.tokens?.let {
                    mapOf(
                        "accessToken" to it.accessToken,
                        "refreshToken" to it.refreshToken,
                    )
                },
            )

            destroyTemporarySession(request)
            response.status = HttpServletResponse.SC_OK
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(response.writer, body)
        } catch (_: RuntimeException) {
            failureHandler.onAuthenticationFailure(
                request,
                response,
                InternalAuthenticationServiceException("Social login failed"),
            )
        }
    }
}

@Component
class OAuth2LoginFailureHandler(
    private val problemHandler: SecurityProblemHandler,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: org.springframework.security.core.AuthenticationException,
    ) {
        destroyTemporarySession(request)
        problemHandler.commence(request, response, exception)
    }
}

private fun destroyTemporarySession(request: HttpServletRequest) {
    request.getSession(false)?.invalidate()
    SecurityContextHolder.clearContext()
}
