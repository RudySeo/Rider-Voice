package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant

class OnboardingTokenTest {

    private val user = User().apply { id = 1L }
    private val issuedAt = Instant.parse("2026-07-23T12:00:00Z")
    private val expiresAt = issuedAt.plusSeconds(5 * 60)

    @Test
    fun `token is usable before expiry but not at the expiry boundary`() {
        val token = OnboardingToken(user, "sha256:token-hash", issuedAt, expiresAt)

        assertThat(token.issuedAt).isEqualTo(issuedAt)
        assertThat(token.isUsableAt(expiresAt.minusNanos(1))).isTrue()
        assertThat(token.isUsableAt(expiresAt)).isFalse()
    }

    @Test
    fun `token can be consumed only once`() {
        val token = OnboardingToken(user, "sha256:token-hash", issuedAt, expiresAt)
        val consumedAt = expiresAt.minusSeconds(1)

        token.consume(consumedAt)

        assertThat(token.consumedAt).isEqualTo(consumedAt)
        assertThat(token.isUsableAt(consumedAt)).isFalse()
        assertThatIllegalStateException()
            .isThrownBy { token.consume(consumedAt) }
    }

    @Test
    fun `token cannot be consumed at or after expiry`() {
        val token = OnboardingToken(user, "sha256:token-hash", issuedAt, expiresAt)

        assertThatIllegalStateException()
            .isThrownBy { token.consume(expiresAt) }
        assertThatIllegalStateException()
            .isThrownBy { token.consume(expiresAt.plusNanos(1)) }
        assertThat(token.consumedAt).isNull()
    }

    @Test
    fun `token hash must not be blank`() {
        assertThatIllegalArgumentException()
            .isThrownBy { OnboardingToken(user, " ", issuedAt, expiresAt) }
    }

    @Test
    fun `token expiry must be exactly five minutes after issuance`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                OnboardingToken(
                    user,
                    "sha256:token-hash",
                    issuedAt,
                    expiresAt.plusNanos(1),
                )
            }
    }
}
