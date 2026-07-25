package com.ridervoice.api.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val accessTokenFilter: OpaqueAccessTokenAuthenticationFilter,
    private val problemHandler: SecurityProblemHandler,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .exceptionHandling {
            it.authenticationEntryPoint(problemHandler)
            it.accessDeniedHandler(problemHandler)
        }
        .authorizeHttpRequests {
            it.requestMatchers(
                HttpMethod.GET,
                "/actuator/health",
                "/actuator/health/**",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/swagger-ui/**",
                "/swagger-ui.html",
            ).permitAll()
            it.requestMatchers(
                HttpMethod.GET,
                "/api/v1/auth/kakao/authorize",
                "/api/v1/auth/kakao/callback",
            ).permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/consents").hasRole("ONBOARDING")
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/search").hasRole("USER")
            it.requestMatchers(HttpMethod.POST, "/api/v1/restaurants").hasRole("USER")
            it.anyRequest().denyAll()
        }
        .addFilterBefore(accessTokenFilter, AnonymousAuthenticationFilter::class.java)
        .build()
}
