package com.ridervoice.api.auth.presentation

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.headers.Header
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
            AUTHORIZATION_PATH,
            PathItem().get(
                Operation()
                    .tags(listOf(AUTHENTICATION_TAG))
                    .summary("카카오 OAuth 로그인 시작")
                    .description(
                        "prompt=login으로 카카오 계정을 다시 인증하도록 authorization endpoint로 redirect하고 " +
                            "state를 임시 HTTP session에 저장합니다.",
                    )
                    .responses(
                        ApiResponses().addApiResponse(
                            "302",
                            ApiResponse().description("카카오 authorization endpoint로 이동"),
                        ),
                    ),
            ),
        )
        paths.addPathItem(
            CALLBACK_PATH,
            PathItem().get(
                Operation()
                    .tags(listOf(AUTHENTICATION_TAG))
                    .summary("카카오 OAuth callback")
                    .description(
                        "OAuth 로그인을 완료한 뒤 HttpOnly refresh cookie를 설정하고 " +
                            "고정된 frontend callback URL로 redirect합니다.",
                    )
                    .addParametersItem(callbackParameter("code"))
                    .addParametersItem(callbackParameter("state"))
                    .responses(
                        ApiResponses()
                            .addApiResponse(
                                "302",
                                ApiResponse()
                                    .description("refresh cookie 설정 후 고정된 frontend callback URL로 이동")
                                    .addHeaderObject(
                                        "Set-Cookie",
                                        Header().description("HttpOnly Rider Voice refresh token cookie")
                                            .schema(StringSchema()),
                                    ),
                            ),
                    ),
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
        const val AUTHORIZATION_PATH = "/api/v1/auth/oauth2/authorization/kakao"
        const val CALLBACK_PATH = "/api/v1/auth/oauth2/callback/kakao"
    }
}
