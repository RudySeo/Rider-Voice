package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserTest {

    @Test
    fun `new user starts active with the user role without terms state`() {
        val user = User()

        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(user.role).isEqualTo(UserRole.USER)
        assertThat(User::class.java.declaredFields.map { it.name })
            .doesNotContain("termsVersion", "termsAgreedAt")
    }
}
