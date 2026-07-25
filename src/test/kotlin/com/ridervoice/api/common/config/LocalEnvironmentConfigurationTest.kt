package com.ridervoice.api.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Files
import java.nio.file.Path

class LocalEnvironmentConfigurationTest {

    @Test
    fun `only the local profile imports the optional root dotenv file`() {
        val local = resourceText("application-local.yml")
        val test = resourceText("application-test.yml")
        val prod = resourceText("application-prod.yml")

        assertThat(local).contains("optional:file:./.env[.properties]")
        assertThat(test).doesNotContain(".env")
        assertThat(prod).doesNotContain(".env")
    }

    @Test
    fun `Kakao local key falls back to the OAuth REST API key`() {
        val application = resourceText("application.yml")
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(
                MapPropertySource("test", mapOf("KAKAO_CLIENT_ID" to "oauth-rest-api-key")),
            )
        }

        assertThat(application)
            .contains("rest-api-key: \${KAKAO_LOCAL_REST_API_KEY:\${KAKAO_CLIENT_ID:}}")
        assertThat(environment.resolvePlaceholders("\${KAKAO_LOCAL_REST_API_KEY:\${KAKAO_CLIENT_ID:}}"))
            .isEqualTo("oauth-rest-api-key")
    }

    @Test
    fun `dedicated Kakao local key overrides the OAuth REST API key`() {
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(
                MapPropertySource(
                    "test",
                    mapOf(
                        "KAKAO_CLIENT_ID" to "oauth-rest-api-key",
                        "KAKAO_LOCAL_REST_API_KEY" to "dedicated-local-api-key",
                    ),
                ),
            )
        }

        assertThat(environment.resolvePlaceholders("\${KAKAO_LOCAL_REST_API_KEY:\${KAKAO_CLIENT_ID:}}"))
            .isEqualTo("dedicated-local-api-key")
    }

    private fun resourceText(fileName: String): String = Files.readString(
        Path.of("src/main/resources", fileName),
    )
}
