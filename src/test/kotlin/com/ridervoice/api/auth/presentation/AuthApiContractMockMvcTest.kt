package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
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
@Import(
    OpenApiConfiguration::class,
    AuthOpenApiConfiguration::class,
    AuthResponseMapper::class,
    AuthCookieManager::class,
    GlobalExceptionHandler::class,
    SecurityProblemHandler::class,
)
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
    private lateinit var refreshSession: RefreshSessionUseCase

    @MockitoBean
    private lateinit var logout: LogoutUseCase

    @MockitoBean
    private lateinit var getCurrentUser: GetCurrentUserUseCase

    @MockitoBean
    private lateinit var accessTokenAuthenticator: AccessTokenAuthenticator

    @Test
    fun `generated OpenAPI exposes a cookie callback without an exchange endpoint`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/v1/auth/oauth2/authorization/kakao'].get.security") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/authorization/kakao'].get.description") {
                    value(containsString("prompt=login"))
                }
                jsonPath("$.paths['/api/v1/auth/oauth2/authorization/kakao'].get.responses['302']") { exists() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.security") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['302']") { exists() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['200']") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/oauth2/callback/kakao'].get.responses['302'].headers['Set-Cookie']") { exists() }
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
    fun `generated OpenAPI exposes bearer and refresh cookie schemes`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
                jsonPath("$.components.securitySchemes.bearerAuth.description") { value(containsString("ROLE_USER")) }
                jsonPath("$.components.securitySchemes.bearerAuth.description") { value(containsString("ROLE_ADMIN")) }
                jsonPath("$.components.securitySchemes.refreshCookie.type") { value("apiKey") }
                jsonPath("$.components.securitySchemes.refreshCookie.in") { value("cookie") }
                jsonPath("$.components.securitySchemes.refreshCookie.name") { value("rider_voice_refresh") }
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
                jsonPath("$.paths['/api/v1/auth/refresh'].post.security[0].refreshCookie") { isArray() }
                jsonPath("$.paths['/api/v1/auth/refresh'].post.requestBody") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/AccessSessionResponse")
                }
                jsonPath("$.components.schemas.AccessSessionResponse.properties.accessToken.type") { value("string") }
                jsonPath("$.components.schemas.AccessSessionResponse.properties.refreshToken") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].refreshCookie") { isArray() }
                jsonPath("$.paths['/api/v1/auth/logout'].post.requestBody") { doesNotExist() }
                jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']") { exists() }
                jsonPath("$.paths['/api/v1/auth/logout'].post.parameters") { doesNotExist() }
                jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth") { isArray() }
                jsonPath("$.paths['/api/v1/users/me'].get.parameters") { doesNotExist() }
                jsonPath("$.paths['/api/v1/users/me'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/UserResponse")
                }
                jsonPath("$.components.schemas.UserResponse.properties.id.type") { value("integer") }
                jsonPath("$.components.schemas.UserResponse.properties.id.format") { value("int64") }
                jsonPath("$.components.schemas.UserResponse.properties.role.enum[0]") { value("USER") }
                jsonPath("$.components.schemas.UserResponse.properties.role.enum[1]") { value("ADMIN") }
            }
    }

    @Test
    fun `refresh failure returns stable problem detail without leaking token to response or logs`(output: CapturedOutput) {
        val rawRefreshToken = "refresh-token-should-never-leak"
        `when`(refreshSession.refresh(RefreshSessionCommand(rawRefreshToken)))
            .thenThrow(AuthenticationRequiredException("Invalid refresh token: $rawRefreshToken"))

        mockMvc.post("/api/v1/auth/refresh") {
            cookie(jakarta.servlet.http.Cookie(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken))
        }.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:authentication-required") }
            jsonPath("$.status") { value(401) }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            jsonPath("$.detail") { value("Authentication is required.") }
            content { string(not(containsString(rawRefreshToken))) }
            header { string("Set-Cookie", containsString("Max-Age=0")) }
        }

        assertThat(output.all).doesNotContain(rawRefreshToken)
    }
}
