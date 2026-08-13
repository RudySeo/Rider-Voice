package com.ridervoice.api.common.security

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

class SecurityPolicyMockMvcTest {

    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        context = AnnotationConfigWebApplicationContext()
        context.servletContext = MockServletContext()
        context.register(SecurityPolicyTestConfiguration::class.java)
        context.refresh()
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Test
    fun `target API authorization matrix is enforced at runtime`() {
        API_MATRIX.forEach { endpoint ->
            CREDENTIALS.forEach { credential ->
                val expectedStatus = endpoint.scope.expectedStatus(credential.scope)
                mockMvc.perform(
                    request(endpoint.method, endpoint.path).apply {
                        credential.token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
                    },
                ).andExpect(status().`is`(expectedStatus))
            }
        }
    }

    @Test
    fun `legacy review draft paths are denied by default even for a user token`() {
        mockMvc.post("/api/v1/review-drafts/fixture") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/users/me/review-drafts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    @Test
    fun `an authenticated request to an unspecified API is forbidden`() {
        mockMvc.get("/api/v1/denied") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    @Test
    fun `authentication failures do not expose bearer tokens provider details or stack traces`() {
        val rawToken = "invalid-provider-secret-token"

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $rawToken")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            content { string(not(containsString(rawToken))) }
            content { string(not(containsString("provider"))) }
            content { string(not(containsString("secret"))) }
            content { string(not(containsString("stackTrace"))) }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(
        SecurityConfig::class,
        OpaqueAccessTokenAuthenticationFilter::class,
        SecurityProblemHandler::class,
        SecurityPolicyFixtureController::class,
    )
    class SecurityPolicyTestConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { accessToken ->
            when (accessToken) {
                "valid-access-token" -> AuthenticatedUserPrincipal(TEST_USER_ID)
                "valid-admin-token" -> AuthenticatedUserPrincipal(
                    TEST_ADMIN_ID,
                    AuthenticatedUserPrincipal.ADMIN_AUTHORITY,
                )
                else -> null
            }
        }
    }

    private data class Endpoint(
        val method: HttpMethod,
        val path: String,
        val scope: Scope,
    )

    private data class Credential(
        val token: String?,
        val scope: Scope?,
    )

    private enum class Scope {
        PUBLIC,
        USER,
        ADMIN,
        ;

        fun expectedStatus(actual: Scope?): Int = when {
            this == PUBLIC -> 200
            actual == null -> 401
            this == actual -> 200
            else -> 403
        }
    }

    private companion object {
        val CREDENTIALS = listOf(
            Credential(null, null),
            Credential("valid-access-token", Scope.USER),
            Credential("valid-admin-token", Scope.ADMIN),
        )

        val API_MATRIX = listOf(
            Endpoint(HttpMethod.GET, "/api/v1/restaurants/search", Scope.PUBLIC),
            Endpoint(HttpMethod.GET, "/api/v1/restaurants/10", Scope.PUBLIC),
            Endpoint(HttpMethod.GET, "/api/v1/restaurants/10/reviews", Scope.PUBLIC),
            Endpoint(HttpMethod.POST, "/api/v1/auth/refresh", Scope.PUBLIC),
            Endpoint(HttpMethod.POST, "/api/v1/auth/logout", Scope.PUBLIC),
            Endpoint(HttpMethod.GET, "/api/v1/users/me", Scope.USER),
            Endpoint(HttpMethod.GET, "/api/v1/addresses/search", Scope.USER),
            Endpoint(HttpMethod.POST, "/api/v1/reviews", Scope.USER),
            Endpoint(HttpMethod.GET, "/api/v1/users/me/reviews", Scope.USER),
            Endpoint(HttpMethod.PATCH, "/api/v1/reviews/10", Scope.USER),
            Endpoint(HttpMethod.DELETE, "/api/v1/reviews/10", Scope.USER),
            Endpoint(HttpMethod.POST, "/api/v1/reviews/10/reports", Scope.USER),
            Endpoint(HttpMethod.POST, "/api/v1/restaurants/10/reports", Scope.USER),
            Endpoint(HttpMethod.GET, "/api/v1/admin/review-reports", Scope.ADMIN),
            Endpoint(HttpMethod.PATCH, "/api/v1/admin/review-reports/10", Scope.ADMIN),
            Endpoint(HttpMethod.GET, "/api/v1/admin/restaurant-reports", Scope.ADMIN),
            Endpoint(HttpMethod.PATCH, "/api/v1/admin/restaurant-reports/10", Scope.ADMIN),
            Endpoint(HttpMethod.PATCH, "/api/v1/admin/restaurants/10/pickup-location", Scope.ADMIN),
        )
    }
}

private const val TEST_USER_ID = 42L
private const val TEST_ADMIN_ID = 43L

@RestController
@TestComponent
private class SecurityPolicyFixtureController {
    @GetMapping(
        "/actuator/health",
        "/swagger-ui.html",
        "/v3/api-docs",
    )
    fun publicGet() = "ok"

    @PostMapping(
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
    )
    fun publicPost() = "ok"

    @GetMapping("/api/v1/users/me")
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        authentication: Authentication,
    ) = "${principal::class.simpleName}:${principal.userId}:${authentication.authorities.single().authority}"

    @GetMapping("/api/v1/restaurants/search")
    fun searchRestaurants() = "ok"

    @GetMapping("/api/v1/restaurants/{restaurantId}")
    fun restaurantDetail() = "ok"

    @GetMapping("/api/v1/restaurants/{restaurantId}/reviews")
    fun publicReviews() = "ok"

    @GetMapping("/api/v1/addresses/search")
    fun searchAddresses() = "ok"

    @PostMapping("/api/v1/reviews")
    fun createReview() = "ok"

    @GetMapping("/api/v1/users/me/reviews")
    fun myReviews() = "ok"

    @org.springframework.web.bind.annotation.PatchMapping("/api/v1/reviews/{reviewId}")
    fun updateReview() = "ok"

    @org.springframework.web.bind.annotation.DeleteMapping("/api/v1/reviews/{reviewId}")
    fun deleteReview() = "ok"

    @PostMapping("/api/v1/reviews/{reviewId}/reports")
    fun reportReview() = "ok"

    @PostMapping("/api/v1/restaurants/{restaurantId}/reports")
    fun reportRestaurant() = "ok"

    @GetMapping(
        "/api/v1/admin/review-reports",
        "/api/v1/admin/restaurant-reports",
    )
    fun adminQueues() = "ok"

    @org.springframework.web.bind.annotation.PatchMapping(
        "/api/v1/admin/review-reports/{reportId}",
        "/api/v1/admin/restaurant-reports/{reportId}",
        "/api/v1/admin/restaurants/{restaurantId}/pickup-location",
    )
    fun adminPatches() = "ok"

    @GetMapping("/api/v1/denied")
    fun denied() = "denied"
}
