package com.ridervoice.api.restaurant.infrastructure.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PublicKakaoKeywordSearchPort
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration(proxyBeanMethods = false)
class PublicSearchCacheConfiguration {

    /** In-process cache for the single API instance; it is intentionally not shared across instances. */
    @Bean
    fun publicSearchCacheManager(utcClock: Clock): CacheManager =
        CaffeineCacheManager(PUBLIC_KAKAO_KEYWORD_SEARCH_CACHE).apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .expireAfterWrite(CACHE_TTL)
                    .ticker { TimeUnit.MILLISECONDS.toNanos(utcClock.millis()) },
            )
        }

    @Bean
    fun publicKakaoKeywordSearchPort(
        kakaoKeywordSearchPort: KakaoKeywordSearchPort,
        publicSearchCacheManager: CacheManager,
    ): PublicKakaoKeywordSearchPort = CachedPublicKakaoKeywordSearchAdapter(
        kakaoKeywordSearchPort,
        publicSearchCacheManager,
    )

    companion object {
        const val PUBLIC_KAKAO_KEYWORD_SEARCH_CACHE = "publicKakaoKeywordSearch"
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}

class CachedPublicKakaoKeywordSearchAdapter(
    private val delegate: KakaoKeywordSearchPort,
    cacheManager: CacheManager,
) : PublicKakaoKeywordSearchPort {
    private val cache = requireNotNull(
        cacheManager.getCache(PublicSearchCacheConfiguration.PUBLIC_KAKAO_KEYWORD_SEARCH_CACHE),
    )

    override fun search(
        query: String,
        limit: Int,
    ): ProviderSearchResult<ExternalRestaurantCandidate> {
        val normalizedQuery = RestaurantNormalization.displayText(query)
        cachedAvailable(normalizedQuery)?.let { return it }

        return delegate.search(normalizedQuery, limit).also { result ->
            if (result is ProviderSearchResult.Available) {
                cache.put(normalizedQuery, result)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun cachedAvailable(query: String): ProviderSearchResult.Available<ExternalRestaurantCandidate>? =
        cache.get(query)?.get() as? ProviderSearchResult.Available<ExternalRestaurantCandidate>
}
