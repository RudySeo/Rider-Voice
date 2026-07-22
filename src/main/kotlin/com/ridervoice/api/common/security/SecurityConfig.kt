package com.ridervoice.api.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/swagger-ui/**",
                "/swagger-ui.html",
            ).permitAll()
            it.anyRequest().permitAll()
        }
        .build()
}
