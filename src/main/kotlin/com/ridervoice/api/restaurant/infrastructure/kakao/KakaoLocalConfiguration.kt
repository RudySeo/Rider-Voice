package com.ridervoice.api.restaurant.infrastructure.kakao

import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoLocalProperties::class)
class KakaoLocalConfiguration {

    @Bean
    fun kakaoLocalClient(properties: KakaoLocalProperties) = KakaoLocalClient(properties)

    @Bean
    fun kakaoKeywordSearchPort(client: KakaoLocalClient): KakaoKeywordSearchPort =
        KakaoKeywordSearchAdapter(client)

    @Bean
    fun kakaoAddressSearchPort(client: KakaoLocalClient): KakaoAddressSearchPort =
        KakaoAddressSearchAdapter(client)
}
