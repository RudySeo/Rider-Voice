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

    @Test
    fun `only a user is promoted to rider and existing privileged roles remain unchanged`() {
        val user = User()
        val rider = User().also { it.promoteToRider() }
        val admin = User().also { setRole(it, UserRole.ADMIN) }

        assertThat(user.promoteToRider()).isTrue()
        assertThat(user.role).isEqualTo(UserRole.RIDER)
        assertThat(rider.promoteToRider()).isFalse()
        assertThat(admin.promoteToRider()).isFalse()
        assertThat(admin.role).isEqualTo(UserRole.ADMIN)
    }

    private fun setRole(user: User, role: UserRole) {
        User::class.java.getDeclaredField("role").also { it.isAccessible = true; it.set(user, role) }
    }
}
