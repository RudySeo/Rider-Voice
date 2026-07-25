package com.ridervoice.api.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    fun `Kakao Local REST key falls back to the Kakao OAuth client id`() {
        val application = resourceText("application.yml")

        assertThat(application)
            .contains("api-key: ${'$'}{KAKAO_LOCAL_REST_API_KEY:${'$'}{KAKAO_CLIENT_ID:}}")
    }

    private fun resourceText(fileName: String): String = Files.readString(
        Path.of("src/main/resources", fileName),
    )
}
