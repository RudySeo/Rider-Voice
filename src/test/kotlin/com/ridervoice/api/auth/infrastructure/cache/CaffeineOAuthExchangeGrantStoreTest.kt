package com.ridervoice.api.auth.infrastructure.cache

import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CaffeineOAuthExchangeGrantStoreTest {

    private val now = Instant.parse("2026-07-29T00:00:00Z")

    @Test
    fun `consume atomically succeeds only once for concurrent requests`() {
        val store = CaffeineOAuthExchangeGrantStore()
        val grant = OAuthExchangeGrant(42L, now.plusSeconds(60))
        store.save("hashed-code", grant)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val futures = (1..2).map {
            executor.submit<OAuthExchangeGrant?> {
                ready.countDown()
                start.await()
                store.consume("hashed-code", now)
            }
        }
        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        val results = futures.map { it.get(2, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertThat(results.count { it == grant }).isEqualTo(1)
        assertThat(results.count { it == null }).isEqualTo(1)
    }

    @Test
    fun `expired grant is consumed without returning user data`() {
        val store = CaffeineOAuthExchangeGrantStore()
        store.save("hashed-code", OAuthExchangeGrant(42L, now))

        assertThat(store.consume("hashed-code", now)).isNull()
        assertThat(store.consume("hashed-code", now.minusMillis(1))).isNull()
    }
}
