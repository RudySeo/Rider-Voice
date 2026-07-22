package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant

class OAuthLoginStateTest {

    @Test
    fun `login state is one-time and expires at its boundary`() {
        val expiresAt = Instant.parse("2026-07-22T12:10:00Z")
        val state = OAuthLoginState("sha256:state-hash", expiresAt)
        val consumedAt = expiresAt.minusSeconds(1)

        assertThat(state.isUsableAt(consumedAt)).isTrue()
        assertThat(state.isUsableAt(expiresAt)).isFalse()

        state.consume(consumedAt)

        assertThat(state.consumedAt).isEqualTo(consumedAt)
        assertThat(state.isUsableAt(consumedAt)).isFalse()
        assertThatIllegalStateException()
            .isThrownBy { state.consume(consumedAt) }
    }
}
