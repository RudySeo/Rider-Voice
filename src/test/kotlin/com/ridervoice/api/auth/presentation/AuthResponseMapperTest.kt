package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthResponseMapperTest {

    private val mapper = AuthResponseMapper()

    @Test
    fun `maps an application session to a mobile token response`() {
        val user = UserSummary(42L, "ACTIVE", UserRole.ADMIN)
        val session = mapper.toMobileSessionResponse(AuthTokens("next-access", "next-refresh", user))
        assertThat(session.accessToken).isEqualTo("next-access")
        assertThat(session.refreshToken).isEqualTo("next-refresh")
        assertThat(session.user.id).isEqualTo(42L)
        assertThat(session.user.role).isEqualTo(UserRole.ADMIN)
    }
}
