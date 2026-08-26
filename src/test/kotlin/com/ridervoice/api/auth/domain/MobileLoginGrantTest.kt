package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class MobileLoginGrantTest {
    private val now = Instant.parse("2026-08-26T00:00:00Z")

    @Test
    fun `grant can be consumed exactly once before expiry`() {
        val grant = MobileLoginGrant(User(), "hashed-code", now.plusSeconds(120))

        assertThat(grant.consume(now.plusSeconds(119))).isTrue()
        assertThat(grant.consumedAt).isEqualTo(now.plusSeconds(119))
        assertThat(grant.consume(now.plusSeconds(119))).isFalse()
    }

    @Test
    fun `grant cannot be consumed at or after expiry`() {
        val grant = MobileLoginGrant(User(), "hashed-code", now.plusSeconds(120))

        assertThat(grant.consume(now.plusSeconds(120))).isFalse()
        assertThat(grant.consumedAt).isNull()
    }
}
