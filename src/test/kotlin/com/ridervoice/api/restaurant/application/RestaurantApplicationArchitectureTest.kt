package com.ridervoice.api.restaurant.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class RestaurantApplicationArchitectureTest {

    @Test
    fun `restaurant application does not import presentation infrastructure or review`() {
        val applicationRoot = Path.of(
            "src/main/kotlin/com/ridervoice/api/restaurant/application",
        )
        val forbiddenImports = listOf(
            "com.ridervoice.api.restaurant.presentation",
            "com.ridervoice.api.restaurant.infrastructure",
            "com.ridervoice.api.review",
        )

        val violations = Files.walk(applicationRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .flatMap { file ->
                    file.readText().lineSequence()
                        .filter { line -> forbiddenImports.any(line::contains) }
                        .map { line -> "${applicationRoot.relativize(file)}: ${line.trim()}" }
                        .toList()
                        .stream()
                }
                .toList()
        }

        assertThat(violations).isEmpty()
    }
}
