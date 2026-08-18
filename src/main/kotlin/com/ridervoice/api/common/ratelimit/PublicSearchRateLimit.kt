package com.ridervoice.api.common.ratelimit

import com.ridervoice.api.common.error.PublicSearchRateLimitExceededException
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class InMemoryPublicSearchRateLimiter(
    private val clock: Clock,
    private val capacity: Int = REQUESTS_PER_MINUTE,
    private val refillWindow: Duration = Duration.ofMinutes(1),
) {
    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    fun tryAcquire(callerKey: String): Boolean {
        val now = clock.instant()
        val bucket = buckets.computeIfAbsent(callerKey) {
            TokenBucket(capacity.toDouble(), now)
        }

        synchronized(bucket) {
            bucket.refill(now, capacity, refillWindow)
            if (bucket.tokens < 1.0) return false
            bucket.tokens -= 1.0
            return true
        }
    }

    private class TokenBucket(
        var tokens: Double,
        var lastRefillAt: Instant,
    ) {
        fun refill(now: Instant, capacity: Int, refillWindow: Duration) {
            if (!now.isAfter(lastRefillAt)) return

            val elapsedNanos = Duration.between(lastRefillAt, now).toNanos().toDouble()
            val refillTokens = elapsedNanos * capacity / refillWindow.toNanos()
            tokens = min(capacity.toDouble(), tokens + refillTokens)
            lastRefillAt = now
        }
    }

    companion object {
        const val REQUESTS_PER_MINUTE = 30
    }
}

class PublicSearchRateLimitInterceptor(
    private val rateLimiter: InMemoryPublicSearchRateLimiter,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (!rateLimiter.tryAcquire(callerKey(request))) {
            throw PublicSearchRateLimitExceededException()
        }
        return true
    }

    private fun callerKey(request: HttpServletRequest): String {
        val principal = SecurityContextHolder.getContext().authentication
            ?.principal as? AuthenticatedUserPrincipal
        return principal?.let { "user:${it.userId}" }
            // In prod, Spring resolves this from the header overwritten by the localhost Nginx proxy.
            // Other profiles continue to use the direct peer address and ignore forwarded headers.
            ?: "remote:${request.remoteAddr}"
    }
}

@Configuration(proxyBeanMethods = false)
class PublicSearchRateLimitConfiguration {

    /** Per-process token buckets for the single API instance; limits are not distributed. */
    @Bean
    fun publicSearchRateLimiter(utcClock: Clock): InMemoryPublicSearchRateLimiter =
        InMemoryPublicSearchRateLimiter(utcClock)

    @Bean
    fun publicSearchRateLimitInterceptor(
        publicSearchRateLimiter: InMemoryPublicSearchRateLimiter,
    ): PublicSearchRateLimitInterceptor = PublicSearchRateLimitInterceptor(publicSearchRateLimiter)

    @Bean
    fun publicSearchRateLimitWebMvcConfigurer(
        publicSearchRateLimitInterceptor: PublicSearchRateLimitInterceptor,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(publicSearchRateLimitInterceptor)
                .addPathPatterns(PUBLIC_SEARCH_PATH)
        }
    }

    private companion object {
        const val PUBLIC_SEARCH_PATH = "/api/v1/restaurants/search"
    }
}
