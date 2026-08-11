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
import com.ridervoice.api.auth.presentation.dto.TokenRequest
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AuthControllerTest {
    private val refreshSession = mock(RefreshSessionUseCase::class.java)
    private val logout = mock(LogoutUseCase::class.java)
    private val getCurrentUser = mock(GetCurrentUserUseCase::class.java)
    private val mapper = AuthResponseMapper()

    @Test
    fun `refresh maps the request to an application command`() {
        val command = RefreshSessionCommand("refresh-token")
        val tokens = AuthTokens("access-token", "next-refresh-token", activeUser())
        `when`(refreshSession.refresh(command)).thenReturn(tokens)

        val response = AuthController(refreshSession, logout, mapper)
            .refresh(TokenRequest("refresh-token"))

        assertThat(response.accessToken).isEqualTo("access-token")
        assertThat(response.refreshToken).isEqualTo("next-refresh-token")
        verify(refreshSession).refresh(command)
    }

    @Test
    fun `logout passes only the user ID and refresh token to the application port`() {
        val response = AuthController(refreshSession, logout, mapper)
            .logout(AuthenticatedUserPrincipal(42L), TokenRequest("refresh-token"))

        assertThat(response.statusCode.value()).isEqualTo(204)
        verify(logout).logout(LogoutCommand(42L, "refresh-token"))
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
