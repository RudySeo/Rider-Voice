package com.ridervoice.api.restaurant.infrastructure.cache

import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CachedPublicKakaoKeywordSearchAdapterTest {

    @Test
    fun `successful results use normalized query cache until five minute expiry`() {
        val clock = MutableClock(Instant.parse("2026-07-26T00:00:00Z"))
        var providerCalls = 0
        val adapter = cachedAdapter(clock) { query, limit ->
            providerCalls++
            assertThat(query).isEqualTo("강남 분식")
            assertThat(limit).isEqualTo(20)
            ProviderSearchResult.Available(listOf(candidate("kakao-1")))
        }

        assertThat(adapter.search("  강남   분식 ", 20)).isInstanceOf(ProviderSearchResult.Available::class.java)
        assertThat(adapter.search("강남 분식", 20)).isInstanceOf(ProviderSearchResult.Available::class.java)
        assertThat(providerCalls).isEqualTo(1)

        clock.advance(Duration.ofMinutes(5).minusMillis(1))
        adapter.search("강남 분식", 20)
        assertThat(providerCalls).isEqualTo(1)

        clock.advance(Duration.ofMillis(1))
        adapter.search("강남 분식", 20)
        assertThat(providerCalls).isEqualTo(2)
    }

    @Test
    fun `provider failure is not cached`() {
        val clock = MutableClock(Instant.parse("2026-07-26T00:00:00Z"))
        var providerCalls = 0
        val adapter = cachedAdapter(clock) { _, _ ->
            providerCalls++
            if (providerCalls == 1) {
                ProviderSearchResult.Unavailable(ProviderFailureReason.TIMEOUT)
            } else {
                ProviderSearchResult.Available(listOf(candidate("kakao-2")))
            }
        }

        assertThat(adapter.search("검색어", 20)).isInstanceOf(ProviderSearchResult.Unavailable::class.java)
        assertThat(adapter.search("검색어", 20)).isInstanceOf(ProviderSearchResult.Available::class.java)
        adapter.search("검색어", 20)

        assertThat(providerCalls).isEqualTo(2)
    }

    private fun cachedAdapter(
        clock: Clock,
        delegate: KakaoKeywordSearchPort,
    ): CachedPublicKakaoKeywordSearchAdapter {
        val cacheManager = PublicSearchCacheConfiguration().publicSearchCacheManager(clock)
        return CachedPublicKakaoKeywordSearchAdapter(delegate, cacheManager)
    }

    private fun candidate(id: String) = ExternalRestaurantCandidate(
        kakaoPlaceId = id,
        name = "후보",
        standardAddress = "서울 강남구 1",
        lotNumberAddress = null,
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
    )
}

private class MutableClock(
    private var current: Instant,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
