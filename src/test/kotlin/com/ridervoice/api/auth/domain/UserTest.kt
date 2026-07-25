package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant

class UserTest {

    @Test
    fun `new user waits for required terms consent`() {
        val user = User()

        assertThat(user.status).isEqualTo(UserStatus.PENDING_TERMS)
        assertThat(user.role).isEqualTo(UserRole.USER)
        assertThat(user.termsVersion).isNull()
        assertThat(user.termsAgreedAt).isNull()
    }

    @Test
    fun `user supports user and administrator roles`() {
        assertThat(User().role).isEqualTo(UserRole.USER)
        assertThat(User(UserRole.ADMIN).role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    fun `agreeing to terms activates a pending user`() {
        val user = User()
        val agreedAt = Instant.parse("2026-07-22T12:00:00Z")

        user.agreeToTerms("2026-07-01", agreedAt)

        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(user.termsVersion).isEqualTo("2026-07-01")
        assertThat(user.termsAgreedAt).isEqualTo(agreedAt)
    }

    @Test
    fun `terms consent requires a version and cannot activate twice`() {
        val user = User()
        val agreedAt = Instant.parse("2026-07-22T12:00:00Z")

        assertThatIllegalArgumentException()
            .isThrownBy { user.agreeToTerms(" ", agreedAt) }

        user.agreeToTerms("2026-07-01", agreedAt)

        assertThatIllegalStateException()
            .isThrownBy { user.agreeToTerms("2026-08-01", agreedAt.plusSeconds(1)) }
    }
}
