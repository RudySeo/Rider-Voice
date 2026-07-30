package com.ridervoice.api.auth.infrastructure.oauth

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant

class KakaoOAuth2UserServiceTest {

    private lateinit var server: HttpServer
    private lateinit var userInfoUri: String

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        userInfoUri = "http://127.0.0.1:${server.address.port}/v2/user/me"
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `loads only the top-level Kakao id as provider subject`() {
        stubResponse(
            200,
            """{"id":123456789,"kakao_account":{"email":"private@example.com","profile":{"nickname":"private"}}}""",
        )

        val user = KakaoOAuth2UserService().loadUser(userRequest())

        assertThat(user.name).isEqualTo("123456789")
        assertThat(user.attributes).containsExactlyEntriesOf(mapOf("id" to "123456789"))
    }

    @Test
    fun `rejects a user-info response without an id`() {
        stubResponse(200, """{"kakao_account":{"email":"private@example.com"}}""")

        assertSafeFailure("kakao_user_info_failed") {
            KakaoOAuth2UserService().loadUser(userRequest())
        }
    }

    @Test
    fun `rejects a malformed Kakao id`() {
        stubResponse(200, """{"id":"not-a-number"}""")

        assertSafeFailure("invalid_kakao_user_info") {
            KakaoOAuth2UserService().loadUser(userRequest())
        }
    }

    @Test
    fun `converts an empty response to a safe failure`() {
        stubResponse(200, "")

        assertSafeFailure("kakao_user_info_failed") {
            KakaoOAuth2UserService().loadUser(userRequest())
        }
    }

    @Test
    fun `converts provider 4xx to a safe failure without its body`() {
        stubResponse(401, """{"error":"invalid_token","error_description":"provider-secret-detail"}""")

        assertSafeFailure("kakao_user_info_failed") {
            KakaoOAuth2UserService().loadUser(userRequest())
        }
    }

    @Test
    fun `converts provider 5xx to a safe failure without its body`() {
        stubResponse(503, "provider-secret-detail")

        assertSafeFailure("kakao_user_info_failed") {
            KakaoOAuth2UserService().loadUser(userRequest())
        }
    }

    private fun assertSafeFailure(expectedCode: String, operation: () -> Unit) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(OAuth2AuthenticationException::class.java) { exception ->
                assertThat(exception.error.errorCode).isEqualTo(expectedCode)
                assertThat(exception.message).doesNotContain("provider-secret-detail")
                assertThat(exception.message).doesNotContain("private@example.com")
            }
    }

    private fun stubResponse(status: Int, body: String) {
        server.createContext("/v2/user/me") { exchange ->
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun userRequest(): OAuth2UserRequest {
        val registration = ClientRegistration.withRegistrationId("kakao")
            .clientId("test-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .userInfoUri(userInfoUri)
            .userNameAttributeName("id")
            .clientName("Kakao")
            .build()
        val token = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-access-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
        )
        return OAuth2UserRequest(registration, token)
    }
}
