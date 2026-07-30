package com.ridervoice.api.review.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class ReviewApplicationArchitectureTest {

    @Test
    fun `review application contracts do not depend on presentation or infrastructure`() {
        val applicationRoot = Path.of("src/main/kotlin/com/ridervoice/api/review/application")
        val forbiddenImports = listOf(
            "com.ridervoice.api.review.presentation",
            "com.ridervoice.api.review.infrastructure",
            "com.ridervoice.api.restaurant.presentation",
            "com.ridervoice.api.restaurant.infrastructure",
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

    @Test
    fun `review input contracts do not import JPA entities`() {
        val inputRoot = Path.of("src/main/kotlin/com/ridervoice/api/review/application/port/in")
        val forbiddenImports = listOf(
            "import com.ridervoice.api.review.domain.Review",
            "import com.ridervoice.api.restaurant.domain.Restaurant",
            "import jakarta.persistence",
        )

        val violations = Files.walk(inputRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .flatMap { file ->
                    file.readText().lineSequence()
                        .filter { line -> forbiddenImports.any { forbidden -> line.trim() == forbidden } }
                        .map { line -> "${inputRoot.relativize(file)}: ${line.trim()}" }
                        .toList()
                        .stream()
                }
                .toList()
        }

        assertThat(violations).isEmpty()
    }
}
