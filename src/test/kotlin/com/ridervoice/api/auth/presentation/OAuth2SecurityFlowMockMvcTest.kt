package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.port.`in`.CompleteSocialLoginCommand
import com.ridervoice.api.auth.application.port.`in`.CompleteProviderLoginUseCase
import com.ridervoice.api.auth.application.port.`in`.ProviderLoginResult
import com.ridervoice.api.auth.domain.OAuthProvider
import com.ridervoice.api.auth.infrastructure.oauth.KakaoOAuth2UserService
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OAuth2SecurityFlowMockMvcTest {

    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mockMvc: MockMvc
    private lateinit var providerServer: HttpServer

    @BeforeEach
    fun setUp() {
        providerServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        providerServer.createContext("/oauth/token") { exchange ->
            val body = """{"access_token":"provider-access-token","token_type":"Bearer","expires_in":300}"""
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        providerServer.createContext("/v2/user/me") { exchange ->
            val body = """{"id":123456789,"kakao_account":{"email":"must-not-escape@example.com"}}"""
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        providerServer.start()

        OAuth2SecurityFlowTestConfiguration.providerPort = providerServer.address.port
        context = AnnotationConfigWebApplicationContext()
        context.servletContext = MockServletContext()
        context.register(OAuth2SecurityFlowTestConfiguration::class.java)
        context.refresh()
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @AfterEach
    fun tearDown() {
        context.close()
        providerServer.stop(0)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `authorization endpoint redirects with state stored in a temporary session`() {
        val result = beginAuthorization()

        val location = result.response.getHeader("Location")!!
        val session = result.request.getSession(false)
        assertThat(result.response.status).isEqualTo(302)
        assertThat(location).startsWith(providerBaseUri("/oauth/authorize"))
        assertThat(queryParameter(location, "state")).isNotBlank()
        assertThat(session).isNotNull
        assertThat(session!!.attributeNames.toList())
            .anyMatch { it.startsWith(HttpSessionOAuth2AuthorizationRequestRepository::class.java.name) }
    }

    @Test
    fun `valid callback creates exchange code redirects to fixed frontend and destroys temporary session`() {
        val authorization = beginAuthorization()
        val session = authorization.request.getSession(false) as MockHttpSession
        val state = queryParameter(authorization.response.getHeader("Location")!!, "state")

        val callback = mockMvc.get("/api/v1/auth/oauth2/callback/kakao") {
            param("code", "authorization-code")
            param("state", state)
            this.session = session
        }.andExpect {
            status { isFound() }
            redirectedUrl("http://localhost:5173/auth/callback?code=oauth-exchange-code")
        }.andReturn()

        val login = context.getBean(RecordingProviderLoginUseCase::class.java)
        assertThat(login.command).isEqualTo(CompleteSocialLoginCommand(OAuthProvider.KAKAO, "123456789"))
        assertThat(callback.response.getHeader("Location"))
            .doesNotContain("service-access-token", "service-refresh-token", "onboarding-token")
        assertThat(callback.request.getSession(false)).isNull()
        assertThat(session.isInvalid).isTrue()
    }

    @Test
    fun `callback rejects an invalid state with generalized fixed redirect and destroys temporary session`() {
        val authorization = beginAuthorization()
        val session = authorization.request.getSession(false) as MockHttpSession

        val callback = mockMvc.get("/api/v1/auth/oauth2/callback/kakao") {
            param("code", "authorization-code")
            param("state", "wrong-state")
            this.session = session
        }.andExpect {
            status { isFound() }
            redirectedUrl("http://localhost:5173/auth/callback?error=oauth_failed")
        }.andReturn()

        assertThat(callback.response.getHeader("Location"))
            .doesNotContain("wrong-state", "provider-access-token", "stack")
        assertThat(callback.request.getSession(false)).isNull()
        assertThat(session.isInvalid).isTrue()
    }

    @Test
    fun `stateless API chain ignores an authenticated OAuth session`() {
        val oauthUser = DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_USER")),
            mapOf("id" to "123456789"),
            "id",
        )
        val oauthAuthentication = OAuth2AuthenticationToken(oauthUser, oauthUser.authorities, "kakao")
        val session = MockHttpSession()
        val sessionContext = SecurityContextHolder.createEmptyContext().apply {
            authentication = oauthAuthentication
        }
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sessionContext)

        mockMvc.get("/api/v1/users/me") {
            this.session = session
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }
    }

    private fun beginAuthorization() = mockMvc
        .get("/api/v1/auth/oauth2/authorization/kakao")
        .andReturn()

    private fun providerBaseUri(path: String) = "http://127.0.0.1:${providerServer.address.port}$path"

    private fun queryParameter(location: String, name: String): String {
        val rawQuery = URI.create(location).rawQuery
        return rawQuery.split('&')
            .map { it.split('=', limit = 2) }
            .first { it[0] == name }
            .let { URLDecoder.decode(it[1], StandardCharsets.UTF_8) }
    }
}

@TestConfiguration(proxyBeanMethods = false)
@EnableWebMvc
@EnableWebSecurity
@Import(
    OAuth2SecurityConfig::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    OAuth2LoginSuccessHandler::class,
    OAuth2LoginFailureHandler::class,
    AuthResponseMapper::class,
    OAuth2SecurityFlowFixtureController::class,
    RecordingProviderLoginUseCase::class,
)
private class OAuth2SecurityFlowTestConfiguration {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val baseUri = "http://127.0.0.1:$providerPort"
        val registration = ClientRegistration.withRegistrationId("kakao")
            .clientId("test-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/api/v1/auth/oauth2/callback/kakao")
            .authorizationUri("$baseUri/oauth/authorize")
            .tokenUri("$baseUri/oauth/token")
            .userInfoUri("$baseUri/v2/user/me")
            .userNameAttributeName("id")
            .clientName("Kakao")
            .build()
        return InMemoryClientRegistrationRepository(registration)
    }

    @Bean
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> = KakaoOAuth2UserService()

    @Bean
    fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }

    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder().build()

    companion object {
        var providerPort: Int = 0
    }
}

@TestComponent
private class RecordingProviderLoginUseCase : CompleteProviderLoginUseCase {
    lateinit var command: CompleteSocialLoginCommand
    var result = ProviderLoginResult("oauth-exchange-code")

    override fun complete(command: CompleteSocialLoginCommand): ProviderLoginResult {
        this.command = command
        return result
    }
}

@RestController
@TestComponent
private class OAuth2SecurityFlowFixtureController {
    @GetMapping("/api/v1/users/me")
    fun me() = "must-be-protected"
}
