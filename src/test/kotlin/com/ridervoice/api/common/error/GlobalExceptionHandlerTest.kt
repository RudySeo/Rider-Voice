package com.ridervoice.api.common.error

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(ErrorFixtureController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `Bean Validation failure returns a stable problem detail`() {
        mockMvc.post("/test/errors/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:validation-failed") }
            jsonPath("$.title") { value("Validation failed") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.detail") { value("Request validation failed.") }
            jsonPath("$.instance") { value("/test/errors/validation") }
        }
    }

    @Test
    fun `malformed request returns a stable bad request problem detail`() {
        mockMvc.post("/test/errors/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = "{"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.code") { value("BAD_REQUEST") }
            jsonPath("$.detail") { value("The request is invalid.") }
        }
    }

    @Test
    fun `authentication failure returns a stable unauthorized problem detail`() {
        mockMvc.get("/test/errors/authentication").andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            jsonPath("$.detail") { value("Authentication is required.") }
        }
    }

    @Test
    fun `missing resource returns a stable not found problem detail`() {
        mockMvc.get("/test/errors/not-found").andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            jsonPath("$.detail") { value("The requested resource was not found.") }
        }
    }

    @Test
    fun `invalid state returns a stable conflict problem detail`() {
        mockMvc.get("/test/errors/conflict").andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
            jsonPath("$.code") { value("STATE_CONFLICT") }
            jsonPath("$.detail") { value("The request conflicts with the current resource state.") }
        }
    }

    @Test
    fun `rider verification lock returns a retry after header without sensitive detail`() {
        mockMvc.get("/test/errors/rider-rate-limit").andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "900") }
            jsonPath("$.code") { value("RIDER_VERIFICATION_RATE_LIMITED") }
            content { string(not(containsString("123456"))) }
        }
    }

    @Test
    fun `unexpected failure does not expose provider message secret or stack trace`() {
        mockMvc.get("/test/errors/unexpected").andExpect {
            status { isInternalServerError() }
            jsonPath("$.status") { value(500) }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.detail") { value("An unexpected error occurred.") }
            content { string(not(containsString("provider-message"))) }
            content { string(not(containsString("client-secret"))) }
            content { string(not(containsString("GlobalExceptionHandlerTest"))) }
        }
    }
}

data class ErrorFixtureRequest(
    @field:NotBlank
    val name: String,
)

@RestController
@RequestMapping("/test/errors")
class ErrorFixtureController {
    @PostMapping("/validation")
    fun validation(@Valid @RequestBody request: ErrorFixtureRequest) = request

    @GetMapping("/authentication")
    fun authentication(): Nothing = throw InsufficientAuthenticationException("provider-message")

    @GetMapping("/not-found")
    fun notFound(): Nothing = throw ResourceNotFoundException()

    @GetMapping("/conflict")
    fun conflict(): Nothing = throw StateConflictException()

    @GetMapping("/rider-rate-limit")
    fun riderRateLimit(): Nothing = throw RiderVerificationRateLimitException(900)

    @GetMapping("/unexpected")
    fun unexpected(): Nothing = throw RuntimeException("provider-message client-secret")
}
