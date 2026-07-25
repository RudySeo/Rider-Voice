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

    private fun resourceText(fileName: String): String = Files.readString(
        Path.of("src/main/resources", fileName),
    )
}
