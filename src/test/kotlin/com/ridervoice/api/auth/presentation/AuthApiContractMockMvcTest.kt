package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthService
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.security.SecurityProblemHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration

@WebMvcTest(controllers = [AuthController::class, UserController::class])
@Import(OpenApiConfiguration::class, SecurityProblemHandler::class)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class AuthApiContractMockMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `generated OpenAPI exposes callback and consent schemas with separate bearer schemes`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
                jsonPath("$.components.securitySchemes.onboardingBearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.onboardingBearerAuth.scheme") { value("bearer") }
                jsonPath("$.paths['/api/v1/auth/kakao/callback'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/CallbackResult")
                }
                jsonPath("$.components.schemas.CallbackResult.properties.tokens['\$ref']") {
                    value("#/components/schemas/AuthTokens")
                }
                jsonPath("$.components.schemas.CallbackResult.properties.onboardingToken.type") { value("string") }
                jsonPath("$.paths['/api/v1/auth/consents'].post.requestBody.content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/ConsentRequest")
                }
                jsonPath("$.paths['/api/v1/auth/consents'].post.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/AuthTokens")
                }
                jsonPath("$.paths['/api/v1/auth/consents'].post.security[0].onboardingBearerAuth") { isArray() }
            }
    }
}
