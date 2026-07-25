package com.ridervoice.api.auth.infrastructure.oauth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class KakaoOAuthAdapterTest {

    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `token exchange sends the configured client secret`() {
        var form: Map<String, String> = emptyMap()
        startServer { exchange ->
            form = parseForm(exchange.requestBody.bufferedReader().readText())
            exchange.respond(200, """{"access_token":"provider-access-token"}""")
        }

        val token = adapter(clientSecret = "synthetic-client-secret").exchangeCode("authorization-code")

        assertThat(token.value).isEqualTo("provider-access-token")
        assertThat(form).containsEntry("client_id", "test-client-id")
        assertThat(form).containsEntry("client_secret", "synthetic-client-secret")
        assertThat(form).containsEntry("code", "authorization-code")
    }

    @Test
    fun `token exchange omits client secret when it is not configured`() {
        var form: Map<String, String> = emptyMap()
        startServer { exchange ->
            form = parseForm(exchange.requestBody.bufferedReader().readText())
            exchange.respond(200, """{"access_token":"provider-access-token"}""")
        }

        adapter(clientSecret = "").exchangeCode("authorization-code")

        assertThat(form).doesNotContainKey("client_secret")
    }

    private fun adapter(clientSecret: String) = KakaoOAuthAdapter(
        KakaoOAuthProperties(
            clientId = "test-client-id",
            clientSecret = clientSecret,
            redirectUri = "http://localhost:8080/api/v1/auth/kakao/callback",
            tokenUri = "http://127.0.0.1:${requireNotNull(server).address.port}/oauth/token",
        ),
    )

    private fun startServer(handler: (HttpExchange) -> Unit) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/oauth/token", handler)
            executor = Executors.newCachedThreadPool()
            start()
        }
    }

    private fun parseForm(body: String): Map<String, String> = body
        .split('&')
        .associate { parameter ->
            val (name, value) = parameter.split('=', limit = 2)
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
        close()
    }
}
