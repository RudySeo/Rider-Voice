package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.ratelimit.InMemoryPublicSearchRateLimiter
import com.ridervoice.api.common.ratelimit.PublicSearchRateLimitInterceptor
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublicSearchRateLimitMockMvcTest {

    @Test
    fun `public search without a trusted proxy ignores forwarded IP headers and returns stable 429 on request 31`() {
        val search = SearchRestaurantsUseCase {
            RestaurantSearchResult(ExternalSearchStatus.AVAILABLE, emptyList())
        }
        val controller = RestaurantSearchController(search, RestaurantSearchHttpMapper())
        val interceptor = PublicSearchRateLimitInterceptor(
            InMemoryPublicSearchRateLimiter(
                Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
        val mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addInterceptors(interceptor)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        repeat(30) { requestIndex ->
            mockMvc.get("/api/v1/restaurants/search") {
                param("query", "강남 분식")
                header("X-Forwarded-For", "198.51.100.$requestIndex")
                with { request ->
                    request.remoteAddr = "192.0.2.10"
                    request
                }
            }.andExpect {
                status { isOk() }
            }
        }

        mockMvc.get("/api/v1/restaurants/search") {
            param("query", "강남 분식")
            header("X-Forwarded-For", "203.0.113.10")
            with { request ->
                request.remoteAddr = "192.0.2.10"
                request
            }
        }.andExpect {
            status { isTooManyRequests() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:public-search-rate-limit-exceeded") }
            jsonPath("$.status") { value(429) }
            jsonPath("$.code") { value("PUBLIC_SEARCH_RATE_LIMIT_EXCEEDED") }
            jsonPath("$.instance") { value("/api/v1/restaurants/search") }
        }
    }

    @Test
    fun `trusted proxy filter exposes the nginx supplied client IP to the rate limiter`() {
        val search = SearchRestaurantsUseCase {
            RestaurantSearchResult(ExternalSearchStatus.AVAILABLE, emptyList())
        }
        val controller = RestaurantSearchController(search, RestaurantSearchHttpMapper())
        val interceptor = PublicSearchRateLimitInterceptor(
            InMemoryPublicSearchRateLimiter(
                Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
        val builder = MockMvcBuilders.standaloneSetup(controller)
        builder.addFilters<StandaloneMockMvcBuilder>(ForwardedHeaderFilter())
        val mockMvc = builder
            .addInterceptors(interceptor)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        repeat(30) {
            mockMvc.get("/api/v1/restaurants/search") {
                param("query", "강남 분식")
                header("X-Forwarded-For", "198.51.100.10")
                with { request ->
                    request.remoteAddr = "127.0.0.1"
                    request
                }
            }.andExpect {
                status { isOk() }
            }
        }

        mockMvc.get("/api/v1/restaurants/search") {
            param("query", "강남 분식")
            header("X-Forwarded-For", "198.51.100.11")
            with { request ->
                request.remoteAddr = "127.0.0.1"
                request
            }
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/restaurants/search") {
            param("query", "강남 분식")
            header("X-Forwarded-For", "198.51.100.10")
            with { request ->
                request.remoteAddr = "127.0.0.1"
                request
            }
        }.andExpect {
            status { isTooManyRequests() }
        }
    }
}
