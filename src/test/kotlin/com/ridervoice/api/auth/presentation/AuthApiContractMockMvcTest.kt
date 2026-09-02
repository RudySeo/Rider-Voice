package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderUseCase
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.SecurityProblemHandler
import org.hamcrest.Matchers.containsString
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

@WebMvcTest(controllers = [UserController::class])
@Import(
    OpenApiConfiguration::class,
    AuthOpenApiConfiguration::class,
    AuthResponseMapper::class,
    GlobalExceptionHandler::class,
    SecurityProblemHandler::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class AuthApiContractMockMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getCurrentUser: GetCurrentUserUseCase

    @MockitoBean
    private lateinit var verifyRider: VerifyRiderUseCase

    @MockitoBean
    private lateinit var accessTokenAuthenticator: AccessTokenAuthenticator

    @Test
    fun `generated OpenAPI exposes only the mobile OAuth start and deep link callback`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/v1/auth/oauth2/authorization/kakao']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/mobile/oauth2/authorization/kakao'].get.description") {
                    value(containsString("prompt=login"))
                }
                jsonPath("$.paths['/api/v1/auth/mobile/oauth2/authorization/kakao'].get.responses['302']") { exists() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.security") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['302']") { exists() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['200']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['302'].headers['Set-Cookie']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/exchange']") { doesNotExist() }
                jsonPath("$.components.schemas.OAuthExchangeCodeRequest") { doesNotExist() }
                jsonPath("$.components.schemas.AuthTokensResponse") { doesNotExist() }
                jsonPath("$.components.schemas.OAuth2LoginResponse") { doesNotExist() }
                jsonPath("$.components.schemas.ProblemDetail") { exists() }
                jsonPath("$.components.schemas.ProblemDetail.properties.code.type") { value("string") }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.parameters[0].name") { value("code") }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.parameters[1].name") { value("state") }
                jsonPath("$.paths['/api/v1/auth/kakao/authorize']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/kakao/callback']") { doesNotExist() }
            }
    }

    @Test
    fun `generated OpenAPI exposes bearer without browser cookie authentication`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
                jsonPath("$.components.securitySchemes.bearerAuth.description") { value(containsString("ROLE_USER")) }
                jsonPath("$.components.securitySchemes.bearerAuth.description") { value(containsString("ROLE_RIDER")) }
                jsonPath("$.components.securitySchemes.bearerAuth.description") { value(containsString("ROLE_ADMIN")) }
                jsonPath("$.components.securitySchemes.refreshCookie") { doesNotExist() }
                jsonPath("$.components.securitySchemes.onboardingBearerAuth") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/consents']") { doesNotExist() }
                jsonPath("$.components.schemas.ConsentRequest") { doesNotExist() }
            }
    }

    @Test
    fun `generated OpenAPI describes public and scoped authentication lifecycle contracts`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/v1/auth/refresh']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/logout']") { doesNotExist() }
                jsonPath("$.components.schemas.AccessSessionResponse") { doesNotExist() }
                jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth") { isArray() }
                jsonPath("$.paths['/api/v1/users/me'].get.parameters") { doesNotExist() }
                jsonPath("$.paths['/api/v1/users/me'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/UserResponse")
                }
                jsonPath("$.components.schemas.UserResponse.properties.id.type") { value("integer") }
                jsonPath("$.components.schemas.UserResponse.properties.id.format") { value("int64") }
                jsonPath("$.components.schemas.UserResponse.properties.role.enum[0]") { value("USER") }
                jsonPath("$.components.schemas.UserResponse.properties.role.enum[1]") { value("RIDER") }
                jsonPath("$.components.schemas.UserResponse.properties.role.enum[2]") { value("ADMIN") }
                jsonPath("$.paths['/api/v1/users/me/rider-verification'].post.security[0].bearerAuth") { isArray() }
                jsonPath("$.components.schemas.UserResponse.properties.termsVersion") { doesNotExist() }
            }
    }

}
