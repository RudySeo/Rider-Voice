package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthService
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.SecurityProblemHandler
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.Mockito.`when`
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration

@WebMvcTest(controllers = [AuthController::class, UserController::class])
@Import(OpenApiConfiguration::class, GlobalExceptionHandler::class, SecurityProblemHandler::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
@ExtendWith(OutputCaptureExtension::class)
class AuthApiContractMockMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `generated OpenAPI exposes consent schemas with separate bearer schemes`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
                jsonPath("$.components.securitySchemes.onboardingBearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.onboardingBearerAuth.scheme") { value("bearer") }
                jsonPath("$.paths['/api/v1/auth/consents'].post.requestBody.content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/ConsentRequest")
                }
                jsonPath("$.paths['/api/v1/auth/consents'].post.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/AuthTokens")
                }
                jsonPath("$.paths['/api/v1/auth/consents'].post.security[0].onboardingBearerAuth") { isArray() }
            }
    }

    @Test
    fun `generated OpenAPI describes public and scoped authentication lifecycle contracts`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/v1/auth/refresh'].post.security") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/refresh'].post.requestBody.content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/TokenRequest")
                }
                jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/AuthTokens")
                }
                jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].bearerAuth") { isArray() }
                jsonPath("$.paths['/api/v1/auth/logout'].post.requestBody.content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/TokenRequest")
                }
                jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']") { exists() }
                jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth") { isArray() }
                jsonPath("$.paths['/api/v1/users/me'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/UserSummary")
                }
                jsonPath("$.components.schemas.UserSummary.properties.id.type") { value("integer") }
                jsonPath("$.components.schemas.UserSummary.properties.id.format") { value("int64") }
            }
    }

    @Test
    fun `refresh failure returns stable problem detail without leaking token to response or logs`(output: CapturedOutput) {
        val rawRefreshToken = "refresh-token-should-never-leak"
        `when`(authService.refresh(rawRefreshToken))
            .thenThrow(IllegalArgumentException("Invalid refresh token: $rawRefreshToken"))

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$rawRefreshToken"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:bad-request") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.code") { value("BAD_REQUEST") }
            jsonPath("$.detail") { value("The request is invalid.") }
            content { string(not(containsString(rawRefreshToken))) }
        }

        assertThat(output.all).doesNotContain(rawRefreshToken)
    }
}
