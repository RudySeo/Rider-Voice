package com.ridervoice.api.common.security

import com.ridervoice.api.common.error.ApiErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class SecurityProblemHandler : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = write(response, request, ApiErrorCode.AUTHENTICATION_REQUIRED)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = write(response, request, ApiErrorCode.ACCESS_DENIED)

    private fun write(
        response: HttpServletResponse,
        request: HttpServletRequest,
        error: ApiErrorCode,
    ) {
        response.status = error.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"type":"${error.type}","title":"${error.title}","status":${error.status.value()},"detail":"${error.detail}","instance":"${escape(request.requestURI)}","code":"${error.name}"}""",
        )
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
