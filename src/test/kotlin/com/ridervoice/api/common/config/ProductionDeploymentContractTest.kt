package com.ridervoice.api.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProductionDeploymentContractTest {

    @Test
    fun `RDS truststore is scoped to MySQL connections instead of replacing the JVM truststore`() {
        val script = scriptText("deploy.sh")

        assertThat(script)
            .contains(
                "trustCertificateKeyStoreUrl=file:\${TRUSTSTORE_CONTAINER_PATH}",
                "trustCertificateKeyStorePassword=\${TRUSTSTORE_PASSWORD}",
                "trustCertificateKeyStoreType=PKCS12",
                "trustCertificateKeyStore(Url|Password|Type)",
                "DB_URL_WITH_TRUSTSTORE",
                "DB_URL must not contain trustCertificateKeyStore properties",
            )
            .doesNotContain(
                "-Djavax.net.ssl.trustStore",
                "--env \"JAVA_TOOL_OPTIONS=",
            )
    }

    @Test
    fun `EC2 deployment scripts have valid bash syntax`() {
        listOf("bootstrap.sh", "deploy.sh").forEach { fileName ->
            val script = DEPLOYMENT_DIRECTORY.resolve(fileName)
            val process = ProcessBuilder("bash", "-n", script.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }

            assertThat(process.waitFor())
                .withFailMessage("%s failed bash syntax validation:%n%s", fileName, output)
                .isZero()
        }
        assertThat(DEPLOYMENT_DIRECTORY.resolve("monitoring.sh")).doesNotExist()
    }

    private fun scriptText(fileName: String): String = Files.readString(DEPLOYMENT_DIRECTORY.resolve(fileName))

    private companion object {
        val DEPLOYMENT_DIRECTORY: Path = Path.of("deploy", "ec2")
    }
}
