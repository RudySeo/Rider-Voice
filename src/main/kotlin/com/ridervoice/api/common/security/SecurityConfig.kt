package com.ridervoice.api.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.security.web.savedrequest.NullRequestCache

@Configuration
class SecurityConfig(
    private val accessTokenFilter: OpaqueAccessTokenAuthenticationFilter,
    private val problemHandler: SecurityProblemHandler,
) {
    @Bean
    @Order(2)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .requestCache { it.requestCache(NullRequestCache()) }
        .securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
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
                "/actuator/prometheus",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/swagger-ui/**",
                "/swagger-ui.html",
            ).permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/search").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*/reviews").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/mobile/exchange").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/mobile/refresh").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/mobile/logout").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/reviews").hasRole("USER")
            it.requestMatchers(HttpMethod.POST, "/api/v1/reviews/*/reports").hasRole("USER")
            it.requestMatchers(HttpMethod.POST, "/api/v1/restaurants/*/reports").hasRole("USER")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/reviews/*").hasRole("USER")
            it.requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/*").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/users/me/reviews").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/reviews/*").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/addresses/search").hasRole("USER")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/review-reports").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/review-reports/*").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/restaurant-reports").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/restaurant-reports/*").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/restaurants/*/pickup-location").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/restaurants/*/name").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/restaurants/*/status").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/restaurants/*/pickup-location/verified-address").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/reviews/*").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/restaurants/search").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/restaurants/*").hasRole("ADMIN")
            it.requestMatchers(HttpMethod.GET, "/api/v1/admin/moderation-audits").hasRole("ADMIN")
            it.anyRequest().denyAll()
        }
        .addFilterBefore(accessTokenFilter, AnonymousAuthenticationFilter::class.java)
        .build()
}
