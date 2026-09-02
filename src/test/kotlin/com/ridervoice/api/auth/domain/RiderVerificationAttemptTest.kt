package com.ridervoice.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RiderVerificationAttemptTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z")

    @Test
    fun `fifth failure locks the account for fifteen minutes`() {
        val attempt = RiderVerificationAttempt(User())

        repeat(4) { assertThat(attempt.registerFailure(now)).isNull() }
        assertThat(attempt.registerFailure(now)).isEqualTo(now.plus(Duration.ofMinutes(15)))
        assertThat(attempt.isLockedAt(now.plusSeconds(899))).isTrue()
        assertThat(attempt.isLockedAt(now.plusSeconds(900))).isFalse()
    }

    @Test
    fun `a failure after an expired lock starts a new count and success clears state`() {
        val attempt = RiderVerificationAttempt(User())
        repeat(5) { attempt.registerFailure(now) }

        attempt.registerFailure(now.plus(Duration.ofMinutes(15)))
        assertThat(attempt.failedAttemptCount).isEqualTo(1)
        assertThat(attempt.lockedUntil).isNull()

        attempt.clear()
        assertThat(attempt.failedAttemptCount).isZero()
    }
}
