package com.ridervoice.api.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(
    classes = [PrometheusActuatorTest.TestApplication::class],
    properties = [
        "management.endpoints.web.exposure.include=health,prometheus",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class PrometheusActuatorTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `prometheus endpoint exposes JVM process and HTTP histogram metrics`() {
        assertThat(get("/metric-fixture").statusCode()).isEqualTo(200)

        val response = get("/actuator/prometheus")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/plain")
        assertThat(response.body())
            .contains(
                "jvm_memory_used_bytes",
                "process_uptime_seconds",
                "http_server_requests_seconds_bucket",
            )
    }

    private fun get(path: String): HttpResponse<String> = httpClient.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(MetricFixtureController::class)
    class TestApplication {
        @Bean
        fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }
}

@RestController
private class MetricFixtureController {
    @GetMapping("/metric-fixture")
    fun metricFixture() = "ok"
}
