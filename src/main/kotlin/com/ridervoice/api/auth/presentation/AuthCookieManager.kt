package com.ridervoice.api.auth.presentation

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

const val REFRESH_TOKEN_COOKIE_NAME = "rider_voice_refresh"

@Component
class AuthCookieManager(
    @Value("\${ridervoice.auth.refresh-cookie.secure:true}")
    private val secure: Boolean,
) {
    fun writeRefreshToken(response: HttpServletResponse, refreshToken: String) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, REFRESH_TOKEN_MAX_AGE).toString())
    }

    fun clearRefreshToken(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
    }

    private fun refreshCookie(value: String, maxAge: Duration): ResponseCookie = ResponseCookie
        .from(REFRESH_TOKEN_COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path(REFRESH_TOKEN_COOKIE_PATH)
        .maxAge(maxAge)
        .build()

    private companion object {
        const val REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth"
        val REFRESH_TOKEN_MAX_AGE: Duration = Duration.ofDays(30)
    }
}
