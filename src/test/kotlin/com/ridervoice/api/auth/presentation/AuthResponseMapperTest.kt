package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthResponseMapperTest {

    private val mapper = AuthResponseMapper()

    @Test
    fun `maps application login and session results to presentation responses`() {
        val user = UserSummary(42L, "ACTIVE", UserRole.ADMIN, "2026-07-01")
        val activeLogin = mapper.toAuthTokensResponse(
            CompleteSocialLoginResult(
                user = user,
                accessToken = "access-token",
                refreshToken = "refresh-token",
            ),
        )

        assertThat(activeLogin.accessToken).isEqualTo("access-token")
        assertThat(activeLogin.refreshToken).isEqualTo("refresh-token")
        assertThat(activeLogin.user.status).isEqualTo("ACTIVE")

        val session = mapper.toAuthTokensResponse(AuthTokens("next-access", "next-refresh", user))
        assertThat(session.accessToken).isEqualTo("next-access")
        assertThat(session.refreshToken).isEqualTo("next-refresh")
        assertThat(session.user.id).isEqualTo(42L)
        assertThat(session.user.role).isEqualTo(UserRole.ADMIN)
        assertThat(session.user.termsVersion).isEqualTo("2026-07-01")
    }
}
