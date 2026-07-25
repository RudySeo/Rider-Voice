package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.presentation.dto.OAuth2LoginResponse
import com.ridervoice.api.auth.presentation.dto.ServiceTokensResponse
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.PathItem
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ProblemDetail

@Configuration(proxyBeanMethods = false)
class AuthOpenApiConfiguration {

    @Bean
    fun authOAuth2OpenApiCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val components = openApi.components ?: Components().also { openApi.components = it }
        listOf(OAuth2LoginResponse::class.java, ServiceTokensResponse::class.java, ProblemDetail::class.java)
            .flatMap { ModelConverters.getInstance().read(it).entries }
            .forEach { (name, schema) -> components.addSchemas(name, schema) }
        applyNullableLoginContract(components)
        val paths = openApi.paths ?: Paths().also { openApi.paths = it }

        paths.addPathItem(
            AUTHORIZATION_PATH,
            PathItem().get(
                Operation()
                    .tags(listOf(AUTHENTICATION_TAG))
                    .summary("카카오 OAuth 로그인 시작")
                    .description("카카오 authorization endpoint로 redirect하고 state를 임시 HTTP session에 저장합니다.")
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
                    .description("OAuth 로그인을 완료하고 약관 상태에 맞는 Rider Voice token 응답을 반환합니다.")
                    .addParametersItem(callbackParameter("code"))
                    .addParametersItem(callbackParameter("state"))
                    .responses(
                        ApiResponses()
                            .addApiResponse(
                                "200",
                                ApiResponse()
                                    .description("로그인 완료")
                                    .content(jsonContent(OAuth2LoginResponse::class.java.simpleName, APPLICATION_JSON_VALUE)),
                            )
                            .addApiResponse(
                                "401",
                                ApiResponse()
                                    .description("OAuth 인증 실패")
                                    .content(jsonContent("ProblemDetail", APPLICATION_PROBLEM_JSON_VALUE)),
                            ),
                    ),
            ),
        )
    }

    private fun applyNullableLoginContract(components: Components) {
        val loginSchema = requireNotNull(components.schemas[OAuth2LoginResponse::class.java.simpleName])
        loginSchema.types = setOf("object")
        loginSchema.required = listOf("termsAgreed", "onboardingToken", "tokens")
        loginSchema.properties["onboardingToken"]?.types = setOf("string", "null")
        loginSchema.properties["tokens"] = ComposedSchema().oneOf(
            listOf(
                Schema<Any>().apply { `$ref` = "#/components/schemas/${ServiceTokensResponse::class.java.simpleName}" },
                Schema<Any>().apply { types = setOf("null") },
            ),
        )

        requireNotNull(components.schemas[ServiceTokensResponse::class.java.simpleName]).apply {
            types = setOf("object")
            required = listOf("accessToken", "refreshToken")
        }
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

    private fun jsonContent(schemaName: String, mediaType: String) = Content().addMediaType(
        mediaType,
        MediaType().schema(Schema<Any>().apply { `$ref` = "#/components/schemas/$schemaName" }),
    )

    private companion object {
        const val AUTHENTICATION_TAG = "Authentication"
        const val AUTHORIZATION_PATH = "/api/v1/auth/oauth2/authorization/kakao"
        const val CALLBACK_PATH = "/api/v1/auth/oauth2/callback/kakao"
    }
}
