package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.UserSummary
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserQuery
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class UserControllerTest {
    private val getCurrentUser = mock(GetCurrentUserUseCase::class.java)
    private val mapper = AuthResponseMapper()

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
    )
}
