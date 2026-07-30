package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginResult
import com.ridervoice.api.auth.application.port.`in`.ServiceTokens
import com.ridervoice.api.auth.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthResponseMapperTest {

    private val mapper = AuthResponseMapper()

    @Test
    fun `maps application login and session results to presentation responses`() {
        val user = UserSummary(42L, "ACTIVE", UserRole.ADMIN, "2026-07-01")
        val activeLogin = mapper.toOAuth2LoginResponse(
            CompleteSocialLoginResult(
                user = user,
                termsAgreed = true,
                onboardingToken = null,
                tokens = ServiceTokens("access-token", "refresh-token"),
            ),
        )

        assertThat(activeLogin.termsAgreed).isTrue()
        assertThat(activeLogin.onboardingToken).isNull()
        assertThat(activeLogin.tokens?.accessToken).isEqualTo("access-token")
        assertThat(activeLogin.tokens?.refreshToken).isEqualTo("refresh-token")

        val session = mapper.toAuthTokensResponse(AuthTokens("next-access", "next-refresh", user))
        assertThat(session.accessToken).isEqualTo("next-access")
        assertThat(session.refreshToken).isEqualTo("next-refresh")
        assertThat(session.user.id).isEqualTo(42L)
        assertThat(session.user.role).isEqualTo(UserRole.ADMIN)
        assertThat(session.user.termsVersion).isEqualTo("2026-07-01")
    }

    @Test
    fun `maps pending terms login to the mutually exclusive nullable contract`() {
        val response = mapper.toOAuth2LoginResponse(
            CompleteSocialLoginResult(
                user = UserSummary(42L, "PENDING_TERMS", UserRole.USER, null),
                termsAgreed = false,
                onboardingToken = "onboarding-token",
                tokens = null,
            ),
        )

        assertThat(response.termsAgreed).isFalse()
        assertThat(response.onboardingToken).isEqualTo("onboarding-token")
        assertThat(response.tokens).isNull()
    }
}
