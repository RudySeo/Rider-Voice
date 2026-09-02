package com.ridervoice.api.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun riderVoiceOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Rider Voice API")
                .description(
                    "라이더 권한 사용자가 작성한 픽업 경험의 공개 리뷰와 리포트를 제공하는 Rider Voice 서버 API. " +
                        "내부 작성 권한은 공개 신원이나 방문 사실을 보증하지 않습니다.",
                )
                .version("v1"),
        )
        .components(
            Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("opaque")
                        .description("ROLE_USER, ROLE_RIDER 또는 ROLE_ADMIN 권한 API에 사용하는 Rider Voice opaque access token"),
                ),
        )

    @Bean
    fun apiContractOpenApiCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        replaceWithNullableReference(
            openApi,
            ownerSchema = "RestaurantBrandReportResponse",
            property = "metrics",
            referencedSchema = "RestaurantBrandReportMetricsResponse",
        )
        replaceWithNullableReference(
            openApi,
            ownerSchema = "RestaurantPickupLocationReportResponse",
            property = "metrics",
            referencedSchema = "RestaurantPickupLocationReportMetricsResponse",
        )
        openApi.components?.schemas?.forEach { (name, schema) ->
            removeNullFromRequiredProperties(schema)
        }
    }

    private fun replaceWithNullableReference(
        openApi: OpenAPI,
        ownerSchema: String,
        property: String,
        referencedSchema: String,
    ) {
        val owner = openApi.components?.schemas?.get(ownerSchema) ?: return
        owner.properties[property] = ComposedSchema().oneOf(
            listOf(
                Schema<Any>().apply { `$ref` = "#/components/schemas/$referencedSchema" },
                Schema<Any>().apply { types = setOf("null") },
            ),
        )
    }

    private fun removeNullFromRequiredProperties(schema: Schema<*>) {
        schema.required.orEmpty().forEach { property ->
            schema.properties?.get(property)?.let(::removeNullOption)
        }
        schema.allOf.orEmpty().forEach(::removeNullFromRequiredProperties)
    }

    private fun removeNullOption(schema: Schema<*>) {
        schema.types = schema.types?.filterNot { it == "null" }?.toSet()
        schema.oneOf = schema.oneOf?.filterNot(::isNullSchema)
        schema.anyOf = schema.anyOf?.filterNot(::isNullSchema)
        schema.nullable = false
    }

    private fun isNullSchema(schema: Schema<*>): Boolean =
        schema.`$ref` == null && schema.types?.singleOrNull() == "null"

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
