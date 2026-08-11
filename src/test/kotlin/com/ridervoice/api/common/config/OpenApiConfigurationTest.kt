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
        assertThat(openApi.info.description)
            .contains("공개 리뷰")
            .contains("라이더 신분과 실제 방문 여부가 인증되지 않은")
            .doesNotContain("비공개")
            .doesNotContain("방문 인증 리뷰")
    }

    @Test
    fun `OpenAPI registers bearer access token security scheme`() {
        val bearerAuth = requireNotNull(openApi.components.securitySchemes[OpenApiConfiguration.BEARER_AUTH])

        assertThat(bearerAuth.type).isEqualTo(SecurityScheme.Type.HTTP)
        assertThat(bearerAuth.scheme).isEqualTo("bearer")
        assertThat(bearerAuth.bearerFormat).isEqualTo("opaque")
    }

    @Test
    fun `OpenAPI does not register a separate onboarding bearer security scheme`() {
        assertThat(openApi.components.securitySchemes).doesNotContainKey("onboardingBearerAuth")
    }
}
