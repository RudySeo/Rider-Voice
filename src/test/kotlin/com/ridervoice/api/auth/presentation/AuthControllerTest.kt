package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutCommand
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletResponse

class AuthControllerTest {
    private val refreshSession = mock(RefreshSessionUseCase::class.java)
    private val logout = mock(LogoutUseCase::class.java)
    private val getCurrentUser = mock(GetCurrentUserUseCase::class.java)
    private val mapper = AuthResponseMapper()
    private val cookies = AuthCookieManager(secure = false)

    @Test
    fun `refresh maps the request to an application command`() {
        val command = RefreshSessionCommand("refresh-token")
        val tokens = AuthTokens("access-token", "next-refresh-token", activeUser())
        `when`(refreshSession.refresh(command)).thenReturn(tokens)

        val servletResponse = MockHttpServletResponse()
        val response = AuthController(refreshSession, logout, mapper, cookies)
            .refresh("refresh-token", servletResponse)

        assertThat(response.accessToken).isEqualTo("access-token")
        assertThat(servletResponse.getHeader("Set-Cookie"))
            .contains(
                "rider_voice_refresh=next-refresh-token",
                "HttpOnly",
                "SameSite=Lax",
                "Path=/api/v1/auth",
                "Max-Age=2592000",
            )
            .doesNotContain("Secure")
        verify(refreshSession).refresh(command)
    }

    @Test
    fun `logout passes the cookie token to the application port and expires the cookie`() {
        val servletResponse = MockHttpServletResponse()
        val response = AuthController(refreshSession, logout, mapper, cookies)
            .logout("refresh-token", servletResponse)

        assertThat(response.statusCode.value()).isEqualTo(204)
        assertThat(servletResponse.getHeader("Set-Cookie"))
            .contains("rider_voice_refresh=", "Max-Age=0")
        verify(logout).logout(LogoutCommand("refresh-token"))
    }

    @Test
    fun `current user lookup passes only the user ID to the application port`() {
        `when`(getCurrentUser.get(GetCurrentUserQuery(42L))).thenReturn(activeUser())

        val response = UserController(getCurrentUser, mapper)
            .me(AuthenticatedUserPrincipal(42L))

        assertThat(response.id).isEqualTo(42L)
        verify(getCurrentUser).get(GetCurrentUserQuery(42L))
    }

    private fun activeUser() = UserSummary(
        id = 42L,
        status = "ACTIVE",
        role = UserRole.USER,
        termsVersion = "2026-07-01",
    )
}
