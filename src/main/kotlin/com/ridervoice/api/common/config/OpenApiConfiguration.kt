package com.ridervoice.api.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun riderVoiceOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Rider Voice API")
                .description("카카오 로그인 사용자의 비공개 픽업 경험 기록을 제공하는 Rider Voice 서버 API")
                .version("v1"),
        )
        .components(
            Components().addSecuritySchemes(
                BEARER_AUTH,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("opaque"),
            ).addSecuritySchemes(
                ONBOARDING_BEARER_AUTH,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("opaque-onboarding")
                    .description("약관 동의 API에서만 사용하는 5분 유효 일회용 onboarding token"),
            ),
        )

    companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val ONBOARDING_BEARER_AUTH = "onboardingBearerAuth"
    }
}
