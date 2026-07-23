package com.ridervoice.api.restaurant.infrastructure.external

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors

class KakaoLocalAdapterTest {

    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `search sends credentials and restaurant filter then maps only restaurant candidates`() {
        var authorizationHeader: String? = null
        var queryParameters: Map<String, String> = emptyMap()
        startServer { exchange ->
            authorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            queryParameters = parseQuery(exchange.requestURI.rawQuery)
            exchange.respond(
                200,
                """
                {
                  "documents": [
                    {
                      "id": "restaurant-1",
                      "place_name": "라이더 식당",
                      "address_name": "서울 강남구 역삼동 1",
                      "road_address_name": "서울 강남구 테헤란로 1",
                      "category_group_code": "FD6",
                      "x": "127.0276543",
                      "y": "37.4987654"
                    },
                    {
                      "id": "restaurant-2",
                      "place_name": "도로명 없는 식당",
                      "address_name": "서울 강남구 역삼동 2",
                      "road_address_name": "",
                      "category_group_code": "FD6",
                      "x": "127.0276000",
                      "y": "37.4987000"
                    },
                    {
                      "id": "cafe-1",
                      "place_name": "제외할 카페",
                      "address_name": "서울 강남구 역삼동 3",
                      "road_address_name": "서울 강남구 테헤란로 3",
                      "category_group_code": "CE7",
                      "x": "127.0275000",
                      "y": "37.4986000"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        val candidates = adapter().searchByKeyword("강남 맛집")

        assertThat(authorizationHeader).isEqualTo("KakaoAK test-rest-api-key")
        assertThat(queryParameters).containsEntry("query", "강남 맛집")
        assertThat(queryParameters).containsEntry("category_group_code", "FD6")
        assertThat(candidates).hasSize(2)
        assertThat(candidates[0].kakaoPlaceId).isEqualTo("restaurant-1")
        assertThat(candidates[0].name).isEqualTo("라이더 식당")
        assertThat(candidates[0].address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(candidates[0].longitude).isEqualByComparingTo("127.0276543")
        assertThat(candidates[0].latitude).isEqualByComparingTo("37.4987654")
        assertThat(candidates[1].address).isEqualTo("서울 강남구 역삼동 2")
    }

    @Test
    fun `empty provider result returns an empty candidate list`() {
        startServer { it.respond(200, """{"documents": []}""") }

        assertThat(adapter().searchByKeyword("없는 식당")).isEmpty()
    }

    @Test
    fun `timeout is normalized without leaking credentials`() {
        startServer { exchange ->
            Thread.sleep(500)
            exchange.respond(200, """{"documents": []}""")
        }

        val exception = assertThrows<KakaoLocalProviderException> {
            adapter(timeout = Duration.ofMillis(100)).searchByKeyword("느린 식당")
        }

        assertThat(exception.failure).isEqualTo(KakaoLocalFailure.TIMEOUT)
        assertSanitized(exception)
    }

    @Test
    fun `rate limit response is normalized without exposing provider body`() {
        startServer { it.respond(429, """{"message":"provider quota details"}""") }

        val exception = assertThrows<KakaoLocalProviderException> {
            adapter().searchByKeyword("식당")
        }

        assertThat(exception.failure).isEqualTo(KakaoLocalFailure.RATE_LIMITED)
        assertSanitized(exception)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403])
    fun `other 4xx responses are normalized`(status: Int) {
        startServer { it.respond(status, "provider-client-error-details") }

        val exception = assertThrows<KakaoLocalProviderException> {
            adapter().searchByKeyword("식당")
        }

        assertThat(exception.failure).isEqualTo(KakaoLocalFailure.CLIENT_ERROR)
        assertSanitized(exception)
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 502, 503])
    fun `5xx responses are normalized`(status: Int) {
        startServer { it.respond(status, "provider-server-error-details") }

        val exception = assertThrows<KakaoLocalProviderException> {
            adapter().searchByKeyword("식당")
        }

        assertThat(exception.failure).isEqualTo(KakaoLocalFailure.SERVER_ERROR)
        assertSanitized(exception)
    }

    @Test
    fun `malformed JSON is normalized without exposing response content`() {
        startServer { it.respond(200, """{"documents":[{"id":"secret-response-fragment"}""") }

        val exception = assertThrows<KakaoLocalProviderException> {
            adapter().searchByKeyword("식당")
        }

        assertThat(exception.failure).isEqualTo(KakaoLocalFailure.INVALID_RESPONSE)
        assertSanitized(exception)
    }

    private fun adapter(timeout: Duration = Duration.ofSeconds(1)) = KakaoLocalAdapter(
        KakaoLocalProperties(
            restApiKey = "test-rest-api-key",
            baseUrl = "http://127.0.0.1:${requireNotNull(server).address.port}",
            timeout = timeout,
        ),
    )

    private fun startServer(handler: (HttpExchange) -> Unit) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v2/local/search/keyword.json", handler)
            executor = Executors.newCachedThreadPool()
            start()
        }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> = rawQuery
        .split('&')
        .associate { parameter ->
            val (name, value) = parameter.split('=', limit = 2)
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private fun assertSanitized(exception: KakaoLocalProviderException) {
        assertThat(exception.message)
            .doesNotContain("test-rest-api-key")
            .doesNotContain("provider quota details")
            .doesNotContain("provider-client-error-details")
            .doesNotContain("provider-server-error-details")
            .doesNotContain("secret-response-fragment")
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
        close()
    }
}
