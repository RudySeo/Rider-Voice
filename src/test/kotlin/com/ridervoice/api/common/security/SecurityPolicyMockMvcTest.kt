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
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.util.UUID

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
    fun `health Swagger OpenAPI Kakao login callback and refresh are public`() {
        listOf(
            "/actuator/health",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/api/v1/auth/kakao/authorize",
            "/api/v1/auth/kakao/callback",
        ).forEach { path ->
            mockMvc.get(path).andExpect { status { isOk() } }
        }

        mockMvc.post("/api/v1/auth/refresh").andExpect { status { isOk() } }
    }

    @Test
    fun `protected endpoints require a bearer token`() {
        mockMvc.post("/api/v1/auth/consents").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/auth/logout").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/users/me").andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer invalid-access-token")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            content { string(not(containsString("invalid-access-token"))) }
        }

    }

    @Test
    fun `only an onboarding token can access consent`() {
        mockMvc.post("/api/v1/auth/consents") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-onboarding-token")
        }.andExpect {
            status { isOk() }
            content { string("OnboardingPrincipal:$TEST_USER_ID:ROLE_ONBOARDING") }
        }

        mockMvc.post("/api/v1/auth/consents") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    @Test
    fun `valid opaque bearer token supplies the authenticated principal`() {
        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
        }.andExpect {
            status { isOk() }
            content { string("AuthenticatedUserPrincipal:$TEST_USER_ID:ROLE_USER") }
        }
    }

    @Test
    fun `an onboarding token cannot access user scoped or unspecified APIs`() {
        listOf("/api/v1/users/me", "/api/v1/denied").forEach { path ->
            mockMvc.get(path) {
                header(HttpHeaders.AUTHORIZATION, "Bearer valid-onboarding-token")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
        }

        listOf("/api/v1/auth/logout", "/api/v1/review-drafts/fixture").forEach { path ->
            mockMvc.post(path) {
                header(HttpHeaders.AUTHORIZATION, "Bearer valid-onboarding-token")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
        }
    }

    @Test
    fun `a user token can access user scoped endpoints including future review drafts`() {
        listOf("/api/v1/auth/logout", "/api/v1/review-drafts/fixture").forEach { path ->
            mockMvc.post(path) {
                header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token")
            }.andExpect { status { isOk() } }
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
                "valid-onboarding-token" -> OnboardingPrincipal(TEST_USER_ID)
                else -> null
            }
        }
    }
}

private val TEST_USER_ID: UUID = UUID.fromString("8cc310ff-f4b7-44a7-bf4d-fd865d555d6f")

@RestController
@TestComponent
private class SecurityPolicyFixtureController {
    @GetMapping(
        "/actuator/health",
        "/swagger-ui.html",
        "/v3/api-docs",
        "/api/v1/auth/kakao/authorize",
        "/api/v1/auth/kakao/callback",
    )
    fun publicGet() = "ok"

    @PostMapping("/api/v1/auth/refresh")
    fun publicPost() = "ok"

    @PostMapping("/api/v1/auth/consents")
    fun consent(
        @AuthenticationPrincipal principal: OnboardingPrincipal,
        authentication: Authentication,
    ) = "${principal::class.simpleName}:${principal.userId}:${authentication.authorities.single().authority}"

    @PostMapping("/api/v1/auth/logout")
    fun logout() = "ok"

    @GetMapping("/api/v1/users/me")
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedUserPrincipal,
        authentication: Authentication,
    ) = "${principal::class.simpleName}:${principal.userId}:${authentication.authorities.single().authority}"

    @PostMapping("/api/v1/review-drafts/fixture")
    fun reviewDraft() = "ok"

    @GetMapping("/api/v1/denied")
    fun denied() = "denied"
}
