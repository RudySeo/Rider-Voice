package com.ridervoice.api.auth.presentation

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.PathItem
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ProblemDetail

@Configuration(proxyBeanMethods = false)
class AuthOpenApiConfiguration {

    @Bean
    fun authOAuth2OpenApiCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val components = openApi.components ?: Components().also { openApi.components = it }
        listOf(ProblemDetail::class.java)
            .flatMap { ModelConverters.getInstance().read(it).entries }
            .forEach { (name, schema) -> components.addSchemas(name, schema) }
        applyProblemDetailContract(components)
        val paths = openApi.paths ?: Paths().also { openApi.paths = it }

        paths.addPathItem(
            CALLBACK_PATH,
            PathItem().get(
                Operation()
                    .tags(listOf(AUTHENTICATION_TAG))
                    .summary("카카오 OAuth callback")
                    .description(
                        "OAuth 로그인을 완료한 뒤 일회용 교환 코드를 발급하고 " +
                            "Rider Voice 모바일 deep link로 redirect합니다.",
                    )
                    .addParametersItem(callbackParameter("code"))
                    .addParametersItem(callbackParameter("state"))
                    .responses(
                        ApiResponses()
                            .addApiResponse(
                                "302",
                                ApiResponse().description("일회용 code가 포함된 모바일 deep link로 이동"),
                            ),
                    ),
            ),
        )
        paths.addPathItem(
            MOBILE_AUTHORIZATION_PATH,
            PathItem().get(
                Operation()
                    .tags(listOf(AUTHENTICATION_TAG))
                    .summary("네이티브 카카오 OAuth 로그인 시작")
                    .description(
                        "prompt=login으로 카카오 계정을 다시 인증하도록 authorization endpoint로 redirect하고 " +
                            "state를 임시 HTTP session에 저장합니다.",
                    )
                    .responses(ApiResponses().addApiResponse("302", ApiResponse().description("카카오 authorization endpoint로 이동"))),
            ),
        )
    }

    private fun applyProblemDetailContract(components: Components) {
        requireNotNull(components.schemas[ProblemDetail::class.java.simpleName]).apply {
            types = setOf("object")
            addProperty("code", StringSchema().description("안정적인 Rider Voice 오류 코드"))
            addRequiredItem("code")
        }
    }

    private fun callbackParameter(name: String) = Parameter()
        .name(name)
        .`in`("query")
        .required(true)
        .schema(StringSchema())

    private companion object {
        const val AUTHENTICATION_TAG = "Authentication"
        const val CALLBACK_PATH = "/api/v1/auth/oauth2/callback/kakao"
        const val MOBILE_AUTHORIZATION_PATH = "/api/v1/auth/mobile/oauth2/authorization/kakao"
    }
}
