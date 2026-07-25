package com.ridervoice.api.common.config

import io.swagger.v3.oas.models.security.SecurityScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiConfigurationTest {

    private val openApi = OpenApiConfiguration().riderVoiceOpenApi()

    @Test
    fun `OpenAPI exposes Rider Voice API metadata`() {
        assertThat(openApi.info.title).isEqualTo("Rider Voice API")
        assertThat(openApi.info.version).isEqualTo("v1")
        assertThat(openApi.info.description).contains("비공개 픽업 경험 기록")
    }

    @Test
    fun `OpenAPI registers bearer access token security scheme`() {
        val bearerAuth = requireNotNull(openApi.components.securitySchemes[OpenApiConfiguration.BEARER_AUTH])

        assertThat(bearerAuth.type).isEqualTo(SecurityScheme.Type.HTTP)
        assertThat(bearerAuth.scheme).isEqualTo("bearer")
        assertThat(bearerAuth.bearerFormat).isEqualTo("opaque")
    }

    @Test
    fun `OpenAPI registers a separate onboarding bearer security scheme`() {
        val onboardingBearer = requireNotNull(
            openApi.components.securitySchemes[OpenApiConfiguration.ONBOARDING_BEARER_AUTH],
        )

        assertThat(onboardingBearer.type).isEqualTo(SecurityScheme.Type.HTTP)
        assertThat(onboardingBearer.scheme).isEqualTo("bearer")
        assertThat(onboardingBearer.bearerFormat).isEqualTo("opaque-onboarding")
    }
}
