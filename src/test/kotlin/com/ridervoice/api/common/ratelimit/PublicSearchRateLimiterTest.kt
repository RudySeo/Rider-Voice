package com.ridervoice.api.common.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PublicSearchRateLimiterTest {

    @Test
    fun `same caller allows requests 29 and 30 and rejects request 31`() {
        val clock = MutableRateLimitClock(Instant.parse("2026-07-26T00:00:00Z"))
        val limiter = InMemoryPublicSearchRateLimiter(clock)

        repeat(29) {
            assertThat(limiter.tryAcquire("remote:192.0.2.1")).isTrue()
        }
        assertThat(limiter.tryAcquire("remote:192.0.2.1")).isTrue()
        assertThat(limiter.tryAcquire("remote:192.0.2.1")).isFalse()

        clock.advance(Duration.ofSeconds(2))
        assertThat(limiter.tryAcquire("remote:192.0.2.1")).isTrue()
    }
}

private class MutableRateLimitClock(
    private var current: Instant,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableRateLimitClock(current, zone)

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
