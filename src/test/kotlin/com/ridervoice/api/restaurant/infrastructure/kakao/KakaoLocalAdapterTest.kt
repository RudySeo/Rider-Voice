package com.ridervoice.api.restaurant.infrastructure.kakao

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class KakaoLocalAdapterTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: URI
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private val kakaoLogger = LoggerFactory.getLogger(KakaoLocalClient::class.java) as Logger

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = URI.create("http://127.0.0.1:${server.address.port}")
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        kakaoLogger.addAppender(logAppender)
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
        kakaoLogger.detachAppender(logAppender)
        logAppender.stop()
    }

    @Test
    fun `keyword search sends Kakao authorization and maps restaurant candidates`() {
        val authorization = AtomicReference<String>()
        val requestQuery = AtomicReference<String>()
        stub("/v2/local/search/keyword.json") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            requestQuery.set(exchange.requestURI.query)
            respond(
                exchange,
                200,
                """
                {
                  "documents": [
                    {
                      "id": "1234567890",
                      "place_name": "라이더보이스 강남점",
                      "address_name": "서울 강남구 역삼동 1-1",
                      "road_address_name": "서울 강남구 테헤란로 1",
                      "x": "127.027610",
                      "y": "37.498095"
                    }
                  ],
                  "meta": {"total_count": 1}
                }
                """.trimIndent(),
            )
        }

        val result = keywordAdapter().search("강남 분식", 7)

        assertThat(result).isEqualTo(
            ProviderSearchResult.Available(
                listOf(
                    ExternalRestaurantCandidate(
                        kakaoPlaceId = "1234567890",
                        name = "라이더보이스 강남점",
                        standardAddress = "서울 강남구 테헤란로 1",
                        lotNumberAddress = "서울 강남구 역삼동 1-1",
                        latitude = "37.498095".toBigDecimal(),
                        longitude = "127.027610".toBigDecimal(),
                    ),
                ),
            ),
        )
        assertThat(authorization.get()).isEqualTo("KakaoAK local-rest-key")
        assertThat(requestQuery.get()).contains("query=강남 분식", "size=7")
    }

    @Test
    fun `keyword search caps the provider request size at Kakao maximum`() {
        val requestQuery = AtomicReference<String>()
        stub("/v2/local/search/keyword.json") { exchange ->
            requestQuery.set(exchange.requestURI.query)
            respond(exchange, 200, """{"documents":[],"meta":{"total_count":0}}""")
        }

        val result = keywordAdapter().search("강남 분식", 20)

        assertThat(result)
            .isEqualTo(ProviderSearchResult.Available<ExternalRestaurantCandidate>(emptyList()))
        assertThat(requestQuery.get()).contains("size=15")
    }

    @Test
    fun `address search maps road and lot number addresses and falls back when road address is absent`() {
        val requestQuery = AtomicReference<String>()
        stub("/v2/local/search/address.json") { exchange ->
            requestQuery.set(exchange.requestURI.query)
            respond(
                exchange,
                200,
                """
            {
              "documents": [
                {
                  "address_name": "서울 강남구 테헤란로 1",
                  "x": "127.027610",
                  "y": "37.498095",
                  "address": {"address_name": "서울 강남구 역삼동 1-1"},
                  "road_address": {"address_name": "서울 강남구 테헤란로 1"}
                },
                {
                  "address_name": "서울 종로구 청운동 1",
                  "x": "126.969291",
                  "y": "37.589481",
                  "address": {"address_name": "서울 종로구 청운동 1"},
                  "road_address": null
                }
              ],
              "meta": {"total_count": 2}
            }
                """.trimIndent(),
            )
        }

        val result = addressAdapter().search("서울 강남구 테헤란로 1", 20)

        assertThat(result).isEqualTo(
            ProviderSearchResult.Available(
                listOf(
                    ExternalAddressCandidate(
                        standardAddress = "서울 강남구 테헤란로 1",
                        lotNumberAddress = "서울 강남구 역삼동 1-1",
                        latitude = "37.498095".toBigDecimal(),
                        longitude = "127.027610".toBigDecimal(),
                    ),
                    ExternalAddressCandidate(
                        standardAddress = "서울 종로구 청운동 1",
                        lotNumberAddress = "서울 종로구 청운동 1",
                        latitude = "37.589481".toBigDecimal(),
                        longitude = "126.969291".toBigDecimal(),
                    ),
                ),
            ),
        )
        assertThat(requestQuery.get()).contains("size=20")
    }

    @Test
    fun `keyword and address searches map empty documents to available empty results`() {
        stubJson("/v2/local/search/keyword.json", 200, """{"documents":[],"meta":{"total_count":0}}""")
        stubJson("/v2/local/search/address.json", 200, """{"documents":[],"meta":{"total_count":0}}""")

        assertThat(keywordAdapter().search("없는 음식점", 20))
            .isEqualTo(ProviderSearchResult.Available<ExternalRestaurantCandidate>(emptyList()))
        assertThat(addressAdapter().search("없는 주소", 20))
            .isEqualTo(ProviderSearchResult.Available<ExternalAddressCandidate>(emptyList()))
    }

    @Test
    fun `maps rate limit client and server responses without exposing provider bodies`() {
        val cases = listOf(
            429 to ProviderFailureReason.RATE_LIMITED,
            400 to ProviderFailureReason.CLIENT_ERROR,
            503 to ProviderFailureReason.SERVER_ERROR,
        )

        cases.forEach { (status, expectedReason) ->
            val path = "/status-$status/v2/local/search/keyword.json"
            stubJson(path, status, """{"message":"provider-secret-detail"}""")
            val adapter = keywordAdapter(baseUrl.resolve("/status-$status/"))

            assertThat(adapter.search("강남 분식", 20))
                .isEqualTo(ProviderSearchResult.Unavailable(expectedReason))
        }

        assertThat(logMessages())
            .anyMatch { it.contains("reason=RATE_LIMITED") && it.contains("status=429") }
            .anyMatch { it.contains("reason=CLIENT_ERROR") && it.contains("status=400") }
            .anyMatch { it.contains("reason=SERVER_ERROR") && it.contains("status=503") }
    }

    @Test
    fun `logs provider neutral failure diagnostics without secrets request data or provider bodies`() {
        stubJson(
            "/v2/local/search/keyword.json",
            401,
            """{"message":"provider-secret-detail"}""",
        )

        val result = keywordAdapter().search("sensitive-search-query", 20)

        assertThat(result)
            .isEqualTo(ProviderSearchResult.Unavailable(ProviderFailureReason.CLIENT_ERROR))
        assertThat(logAppender.list).hasSize(1)
        val event = logAppender.list.single()
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.formattedMessage)
            .contains(
                "operation=KEYWORD_SEARCH",
                "reason=CLIENT_ERROR",
                "status=401",
                "exceptionType=",
                "elapsedMs=",
            )
            .doesNotContain(
                "local-rest-key",
                "sensitive-search-query",
                "provider-secret-detail",
                "Authorization",
                "KakaoAK",
            )
        assertThat(event.throwableProxy).isNull()
    }

    @Test
    fun `maps malformed or structurally invalid JSON to invalid response`() {
        val responses = listOf(
            "not-json",
            """{"documents":[{"id":"123","place_name":"이름","x":"invalid","y":"37.1"}]}""",
        )

        responses.forEachIndexed { index, body ->
            val path = "/invalid-$index/v2/local/search/keyword.json"
            stubJson(path, 200, body)

            assertThat(keywordAdapter(baseUrl.resolve("/invalid-$index/")).search("강남 분식", 20))
                .isEqualTo(ProviderSearchResult.Unavailable(ProviderFailureReason.INVALID_RESPONSE))
        }

        assertThat(logMessages())
            .hasSize(2)
            .allMatch { it.contains("reason=INVALID_RESPONSE") && it.contains("status=none") }
            .noneMatch { it.contains("not-json") || it.contains("\"x\":\"invalid\"") }
    }

    @Test
    fun `maps read timeout to timeout failure`() {
        stub("/v2/local/search/keyword.json") { exchange ->
            Thread.sleep(250)
            respond(exchange, 200, """{"documents":[]}""")
        }
        val adapter = keywordAdapter(readTimeout = Duration.ofMillis(30))

        assertThat(adapter.search("강남 분식", 20))
            .isEqualTo(ProviderSearchResult.Unavailable(ProviderFailureReason.TIMEOUT))
        assertThat(logMessages()).hasSize(1)
        assertThat(logMessages().single())
            .contains("operation=KEYWORD_SEARCH", "reason=TIMEOUT", "status=none")
    }

    @Test
    fun `maps connection failure to connection failed`() {
        val unavailableBaseUrl = baseUrl
        server.stop(0)

        assertThat(keywordAdapter(unavailableBaseUrl).search("강남 분식", 20))
            .isEqualTo(ProviderSearchResult.Unavailable(ProviderFailureReason.CONNECTION_FAILED))
        assertThat(logMessages()).hasSize(1)
        assertThat(logMessages().single())
            .contains("operation=KEYWORD_SEARCH", "reason=CONNECTION_FAILED", "status=none")
    }

    @Test
    fun `logs address search operation separately`() {
        stubJson("/v2/local/search/address.json", 503, """{"message":"unavailable"}""")

        assertThat(addressAdapter().search("서울 강남구 테헤란로", 20))
            .isEqualTo(ProviderSearchResult.Unavailable(ProviderFailureReason.SERVER_ERROR))
        assertThat(logMessages()).hasSize(1)
        assertThat(logMessages().single())
            .contains("operation=ADDRESS_SEARCH", "reason=SERVER_ERROR", "status=503")
    }

    private fun keywordAdapter(
        url: URI = baseUrl,
        readTimeout: Duration = Duration.ofSeconds(1),
    ) = KakaoKeywordSearchAdapter(localClient(url, readTimeout))

    private fun addressAdapter() = KakaoAddressSearchAdapter(localClient(baseUrl, Duration.ofSeconds(1)))

    private fun localClient(url: URI, readTimeout: Duration) = KakaoLocalClient(
        KakaoLocalProperties(
            apiKey = "local-rest-key",
            baseUrl = url,
            connectTimeout = Duration.ofMillis(100),
            readTimeout = readTimeout,
        ),
    )

    private fun stubJson(path: String, status: Int, body: String) {
        stub(path) { exchange -> respond(exchange, status, body) }
    }

    private fun stub(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path, handler)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun logMessages(): List<String> = logAppender.list.map(ILoggingEvent::getFormattedMessage)
}
