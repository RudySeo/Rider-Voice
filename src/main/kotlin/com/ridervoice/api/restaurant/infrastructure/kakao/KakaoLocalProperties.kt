package com.ridervoice.api.restaurant.infrastructure.kakao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("ridervoice.restaurant.kakao-local")
data class KakaoLocalProperties(
    val apiKey: String = "",
    val baseUrl: URI = URI.create(DEFAULT_BASE_URL),
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(3),
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://dapi.kakao.com"
    }
}
