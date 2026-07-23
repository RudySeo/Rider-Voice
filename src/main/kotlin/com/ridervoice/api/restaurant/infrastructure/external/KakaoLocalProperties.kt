package com.ridervoice.api.restaurant.infrastructure.external

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kakao.local")
data class KakaoLocalProperties(
    val restApiKey: String = "",
    val baseUrl: String = "https://dapi.kakao.com",
    val timeout: Duration = Duration.ofSeconds(2),
)
