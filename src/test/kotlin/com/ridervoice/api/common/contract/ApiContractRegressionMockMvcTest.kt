package com.ridervoice.api.common.contract

import com.ridervoice.api.auth.application.port.`in`.ExchangeSocialLoginCodeUseCase
import com.ridervoice.api.auth.application.port.`in`.GetCurrentUserUseCase
import com.ridervoice.api.auth.application.port.`in`.LogoutUseCase
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionCommand
import com.ridervoice.api.auth.application.port.`in`.RefreshSessionUseCase
import com.ridervoice.api.auth.presentation.AuthController
import com.ridervoice.api.auth.presentation.AuthOpenApiConfiguration
import com.ridervoice.api.auth.presentation.AuthResponseMapper
import com.ridervoice.api.auth.presentation.OAuthExchangeController
import com.ridervoice.api.auth.presentation.UserController
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsUseCase
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressUseCase
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.GetAdminRestaurantDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsUseCase
import com.ridervoice.api.moderation.presentation.AdminInvestigationController
import com.ridervoice.api.moderation.presentation.AdminModerationController
import com.ridervoice.api.moderation.presentation.AdminRestaurantController
import com.ridervoice.api.moderation.presentation.ModerationHttpMapper
import com.ridervoice.api.moderation.presentation.ModerationInvestigationHttpMapper
import com.ridervoice.api.moderation.presentation.ModerationReportController
import com.ridervoice.api.moderation.presentation.RestaurantAdministrationHttpMapper
import com.ridervoice.api.restaurant.application.PublicRestaurantDetailService
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult
import com.ridervoice.api.restaurant.application.model.PublicRestaurantPickupLocationResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportResult
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportResult
import com.ridervoice.api.restaurant.application.port.`in`.GetPublicRestaurantDetailUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import com.ridervoice.api.restaurant.presentation.AddressSearchController
import com.ridervoice.api.restaurant.presentation.RestaurantDetailController
import com.ridervoice.api.restaurant.presentation.RestaurantDetailHttpMapper
import com.ridervoice.api.restaurant.presentation.RestaurantSearchController
import com.ridervoice.api.restaurant.presentation.RestaurantSearchHttpMapper
import com.ridervoice.api.review.application.PublicReviewListService
import com.ridervoice.api.review.application.model.PublicReviewAuthorActivityResult
import com.ridervoice.api.review.application.model.PublicReviewListItemResult
import com.ridervoice.api.review.application.model.PublicReviewListResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewUseCase
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.review.presentation.MyReviewController
import com.ridervoice.api.review.presentation.PublicReviewController
import com.ridervoice.api.review.presentation.ReviewController
import com.ridervoice.api.review.presentation.ReviewHttpMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(
    controllers = [
        AuthController::class,
        OAuthExchangeController::class,
        UserController::class,
        RestaurantSearchController::class,
        AddressSearchController::class,
        RestaurantDetailController::class,
        ReviewController::class,
        MyReviewController::class,
        PublicReviewController::class,
        ModerationReportController::class,
        AdminModerationController::class,
        AdminRestaurantController::class,
        AdminInvestigationController::class,
    ],
)
@Import(
    OpenApiConfiguration::class,
    AuthOpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    AuthResponseMapper::class,
    RestaurantSearchHttpMapper::class,
    RestaurantDetailHttpMapper::class,
    ReviewHttpMapper::class,
    ModerationHttpMapper::class,
    ModerationInvestigationHttpMapper::class,
    RestaurantAdministrationHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    ApiContractRegressionMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class ApiContractRegressionMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var exchangeSocialLoginCode: ExchangeSocialLoginCodeUseCase
    @MockitoBean private lateinit var refreshSession: RefreshSessionUseCase
    @MockitoBean private lateinit var logout: LogoutUseCase
    @MockitoBean private lateinit var getCurrentUser: GetCurrentUserUseCase
    @MockitoBean private lateinit var accessTokenAuthenticator: AccessTokenAuthenticator
    @MockitoBean private lateinit var searchRestaurants: SearchRestaurantsUseCase
    @MockitoBean private lateinit var searchAddresses: SearchAddressesUseCase
    @MockitoBean private lateinit var getRestaurantDetail: GetPublicRestaurantDetailUseCase
    @MockitoBean private lateinit var createReview: CreateReviewUseCase
    @MockitoBean private lateinit var updateReview: UpdateReviewUseCase
    @MockitoBean private lateinit var deleteReview: DeleteReviewUseCase
    @MockitoBean private lateinit var listMyReviews: ListMyReviewsUseCase
    @MockitoBean private lateinit var listPublicReviews: ListPublicRestaurantReviewsUseCase
    @MockitoBean private lateinit var createReviewReport: CreateReviewReportUseCase
    @MockitoBean private lateinit var createRestaurantReport: CreateRestaurantInfoReportUseCase
    @MockitoBean private lateinit var listReviewReports: ListPendingReviewReportsUseCase
    @MockitoBean private lateinit var decideReviewReport: DecideReviewReportUseCase
    @MockitoBean private lateinit var listRestaurantReports: ListPendingRestaurantInfoReportsUseCase
    @MockitoBean private lateinit var decideRestaurantReport: DecideRestaurantInfoReportUseCase
    @MockitoBean private lateinit var mergeRestaurant: MergeRestaurantUseCase
    @MockitoBean private lateinit var relinkRestaurant: RelinkRestaurantPickupLocationUseCase
    @MockitoBean private lateinit var renameRestaurant: RenameRestaurantUseCase
    @MockitoBean private lateinit var changeRestaurantStatus: ChangeRestaurantStatusUseCase
    @MockitoBean private lateinit var relinkVerifiedAddress: RelinkRestaurantVerifiedAddressUseCase
    @MockitoBean private lateinit var getAdminReviewDetail: GetAdminReviewDetailUseCase
    @MockitoBean private lateinit var searchAdminRestaurants: SearchAdminRestaurantsUseCase
    @MockitoBean private lateinit var getAdminRestaurantDetail: GetAdminRestaurantDetailUseCase
    @MockitoBean private lateinit var listModerationAudits: ListModerationAuditsUseCase

    @Test
    fun `generated OpenAPI exposes the complete MVP path and authentication contract`() {
        val document = openApi()
        val paths = document.required("paths")

        assertThat(paths.propertyNames().asSequence().toSet()).isEqualTo(EXPECTED_PATHS)
        assertThat(apiOperations(paths)).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS + BEARER_OPERATIONS)

        PUBLIC_OPERATIONS.forEach { (path, method) ->
            assertThat(operation(paths, path, method).has("security")).isFalse()
        }
        BEARER_OPERATIONS.forEach { (path, method) ->
            assertSecurity(paths, path, method, "bearerAuth")
        }
        assertThat(document.at("/components/securitySchemes/bearerAuth/bearerFormat").stringValue())
            .isEqualTo("opaque")
        assertThat(document.at("/components/securitySchemes/onboardingBearerAuth").isMissingNode).isTrue()
    }

    @Test
    fun `generated OpenAPI exposes every body and response DTO with correct endpoint refs`() {
        val document = openApi()
        val schemas = document.at("/components/schemas")

        assertThat(schemas.propertyNames().asSequence().toSet()).containsAll(EXPECTED_SCHEMA_NAMES)
        REQUEST_REFS.forEach { (operation, schemaName) ->
            val (path, method) = operation
            assertThat(
                document.at("/paths/${pointer(path)}/$method/requestBody/content/application~1json/schema/\u0024ref")
                    .stringValue(),
            ).isEqualTo("#/components/schemas/$schemaName")
        }
        RESPONSE_REFS.forEach { (operation, response) ->
            val (path, method) = operation
            val (status, schemaName) = response
            val reference = document.at(
                "/paths/${pointer(path)}/$method/responses/$status/content/application~1json/schema/\u0024ref",
            )
            assertThat(reference.isMissingNode)
                .describedAs("$method $path response $status schema reference")
                .isFalse()
            assertThat(reference.stringValue()).isEqualTo("#/components/schemas/$schemaName")
        }
    }

    @Test
    fun `generated OpenAPI exposes enums nullable fields cursors and target discriminator exactly`() {
        val document = openApi()
        val schemas = document.at("/components/schemas")

        ENUM_PROPERTIES.forEach { (schemaAndProperty, expected) ->
            val (schema, property) = schemaAndProperty
            assertThat(enumValues(propertySchema(schemas, schema, property)))
                .describedAs("$schema.$property enum")
                .containsExactlyInAnyOrderElementsOf(expected)
        }
        NULLABLE_PROPERTIES.forEach { (schema, property) ->
            assertThat(isNullable(propertySchema(schemas, schema, property)))
                .describedAs("$schema.$property nullable")
                .isTrue()
        }
        NULLABLE_REFERENCE_PROPERTIES.forEach { (schemaAndProperty, referencedSchema) ->
            val (schema, property) = schemaAndProperty
            assertThat(nullableReferenceOptions(propertySchema(schemas, schema, property)))
                .describedAs("$schema.$property nullable reference")
                .containsExactlyInAnyOrder("#/components/schemas/$referencedSchema", "null")
        }
        NON_NULL_PROPERTIES.forEach { (schema, property) ->
            assertThat(isNullable(propertySchema(schemas, schema, property)))
                .describedAs("$schema.$property non-null")
                .isFalse()
        }

        val target = schemas.required("RestaurantTargetRequest")
        assertThat(target.at("/discriminator/propertyName").stringValue()).isEqualTo("type")
        assertThat(target.required("oneOf").size()).isEqualTo(4)
        assertThat(target.at("/discriminator/mapping").properties().asSequence().associate { it.key to it.value.stringValue() })
            .containsExactlyInAnyOrderEntriesOf(TARGET_MAPPINGS)
        assertThat(requiredProperties(target)).contains("type")
        assertThat(requiredProperties(schemas.required("ExistingRestaurantTargetRequest"))).contains("restaurantId")
        assertThat(requiredProperties(schemas.required("ManualExistingLocationRestaurantTargetRequest")))
            .contains("pickupLocationId", "name", "platforms")
        val correction = schemas.required("RestaurantInfoCorrectionRequest")
        assertThat(correction.at("/discriminator/propertyName").stringValue()).isEqualTo("type")
        assertThat(correction.required("oneOf").size()).isEqualTo(5)
        assertThat(
            correction.at("/discriminator/mapping").properties().asSequence()
                .associate { it.key to it.value.stringValue() },
        ).containsExactlyInAnyOrderEntriesOf(CORRECTION_MAPPINGS)
        assertThat(requiredProperties(correction)).contains("type")
        assertThat(requiredProperties(schemas.required("RenameRestaurantCorrectionRequest"))).contains("name")
        assertThat(requiredProperties(schemas.required("RelinkExistingPickupCorrectionRequest")))
            .contains("pickupLocationId")
        assertThat(requiredProperties(schemas.required("RelinkVerifiedAddressCorrectionRequest")))
            .contains("addressQuery", "selectedStandardAddress")
        assertThat(requiredProperties(schemas.required("MergeRestaurantCorrectionRequest")))
            .contains("canonicalRestaurantId")
        assertThat(propertySchema(schemas, "CreateReviewRequest", "comment").required("maxLength").intValue())
            .isEqualTo(200)

        SEARCH_OPERATIONS.forEach { (path, method) ->
            val query = operation(document.required("paths"), path, method).required("parameters")
                .first { it.required("name").stringValue() == "query" }
            assertThat(query.required("required").booleanValue()).isTrue()
            assertThat(query.at("/schema/minLength").intValue()).isEqualTo(2)
            assertThat(query.at("/schema/maxLength").intValue()).isEqualTo(100)
        }

        CURSOR_OPERATIONS.forEach { (path, method) ->
            val parameters = operation(document.required("paths"), path, method).required("parameters")
            val cursor = parameters.first { it.required("name").stringValue() == "cursor" }
            val size = parameters.first { it.required("name").stringValue() == "size" }
            assertThat(cursor.path("required").asBoolean(false)).isFalse()
            assertThat(isNullable(cursor.required("schema"))).isTrue()
            assertThat(size.at("/schema/default").intValue()).isEqualTo(20)
            assertThat(size.at("/schema/minimum").intValue()).isEqualTo(1)
            assertThat(size.at("/schema/maximum").intValue()).isEqualTo(50)
        }
    }

    @Test
    fun `public review and aggregate report responses always include UNVERIFIED notice`() {
        `when`(getRestaurantDetail.get(10L)).thenReturn(detailResult())
        `when`(listPublicReviews.list(ListPublicRestaurantReviewsCommand(10L, null, 20)))
            .thenReturn(publicReviewResult())

        mockMvc.get("/api/v1/restaurants/10").andExpect {
            status { isOk() }
            jsonPath("$.brandReport") { exists() }
            jsonPath("$.pickupLocationReport") { exists() }
            jsonPath("$.verificationStatus") { value("UNVERIFIED") }
            jsonPath("$.verificationNotice") { value(PublicRestaurantDetailService.VERIFICATION_NOTICE) }
        }
        mockMvc.get("/api/v1/restaurants/10/reviews").andExpect {
            status { isOk() }
            jsonPath("$.items[0].verificationStatus") { value("UNVERIFIED") }
            jsonPath("$.items[0].verificationNotice") { value(PublicReviewListService.VERIFICATION_NOTICE) }
        }
    }

    @Test
    fun `unexpected failures return stable ProblemDetail without provider secret token or stack trace`() {
        val rawToken = "refresh-token-must-not-leak"
        `when`(refreshSession.refresh(RefreshSessionCommand(rawToken))).thenThrow(
            RuntimeException("provider-secret stackTrace $rawToken"),
        )

        val response = mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$rawToken"}"""
        }.andExpect {
            status { isInternalServerError() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
        }.andReturn().response.contentAsString

        assertThat(response)
            .doesNotContain(rawToken, "provider-secret", "RuntimeException", "stackTrace")
    }

    private fun openApi(): JsonNode {
        val body = mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        return objectMapper.readTree(body)
    }

    private fun detailResult() = PublicRestaurantDetailResult(
        restaurantId = 10L,
        name = "공개 브랜드",
        pickupLocation = PublicRestaurantPickupLocationResult(
            pickupLocationId = 20L,
            standardAddress = "서울 강남구 테헤란로 1",
            detailAddress = null,
            latitude = BigDecimal("37.50000000"),
            longitude = BigDecimal("127.00000000"),
        ),
        brandReport = RestaurantBrandReportResult(AggregationStatus.COLLECTING, 1, null),
        pickupLocationReport = RestaurantPickupLocationReportResult(AggregationStatus.COLLECTING, 1, null),
        verificationStatus = "UNVERIFIED",
        verificationNotice = PublicRestaurantDetailService.VERIFICATION_NOTICE,
    )

    private fun publicReviewResult() = PublicReviewListResult(
        items = listOf(
            PublicReviewListItemResult(
                reviewId = 100L,
                visitMonth = VisitMonth.parse("2026-07"),
                ratings = ReviewRatings(
                    pickupSpaceCleanliness = ReviewRating.GOOD,
                    packagingStability = ReviewRating.GOOD,
                    orderReadiness = ReviewRating.GOOD,
                    handoffAccuracy = ReviewRating.GOOD,
                    staffInteraction = ReviewRating.NOT_OBSERVED,
                    riderRespect = ReviewRating.GOOD,
                ),
                comment = null,
                authorActivity = PublicReviewAuthorActivityResult(1, 1L),
                createdAt = Instant.parse("2026-07-25T03:00:00Z"),
                verificationStatus = "UNVERIFIED",
                verificationNotice = PublicReviewListService.VERIFICATION_NOTICE,
            ),
        ),
        nextCursor = null,
    )

    private fun assertSecurity(paths: JsonNode, path: String, method: String, scheme: String) {
        val security = operation(paths, path, method).required("security")
        assertThat(security.size()).isEqualTo(1)
        assertThat(security[0].has(scheme)).isTrue()
        assertThat(security[0].required(scheme).isArray).isTrue()
    }

    private fun operation(paths: JsonNode, path: String, method: String): JsonNode =
        paths.required(path).required(method)

    private fun apiOperations(paths: JsonNode): Set<Pair<String, String>> = paths.properties().asSequence()
        .flatMap { (path, pathItem) ->
            pathItem.propertyNames().asSequence()
                .filter { it in HTTP_METHODS }
                .map { path to it }
        }
        .toSet()

    private fun propertySchema(schemas: JsonNode, schema: String, property: String): JsonNode {
        val schemaNode = schemas.required(schema)
        val direct = schemaNode.path("properties").path(property)
        if (!direct.isMissingNode) return direct
        schemaNode.path("allOf").forEach { part ->
            val nested = part.path("properties").path(property)
            if (!nested.isMissingNode) return nested
        }
        error("OpenAPI property is missing: $schema.$property")
    }

    private fun enumValues(schema: JsonNode): List<String> {
        val direct = schema.path("enum")
        if (direct.isArray) return direct.values().asSequence().map { it.stringValue() }.toList()
        val itemEnum = schema.path("items").path("enum")
        if (itemEnum.isArray) return itemEnum.values().asSequence().map { it.stringValue() }.toList()
        schema.path("allOf").forEach { part ->
            val nested = part.path("enum")
            if (nested.isArray) return nested.values().asSequence().map { it.stringValue() }.toList()
        }
        return emptyList()
    }

    private fun isNullable(schema: JsonNode): Boolean {
        if (schema.path("nullable").asBoolean(false)) return true
        val type = schema.path("type")
        if (type.isString && type.stringValue() == "null") return true
        if (type.isArray && type.any { it.stringValue() == "null" }) return true
        return sequenceOf("oneOf", "anyOf", "allOf")
            .flatMap { schema.path(it).asSequence() }
            .any(::isNullable)
    }

    private fun nullableReferenceOptions(schema: JsonNode): Set<String> = schema.path("oneOf")
        .asSequence()
        .mapNotNull { option ->
            when {
                option.has("\u0024ref") -> option.required("\u0024ref").stringValue()
                option.path("type").stringValue("") == "null" -> "null"
                else -> null
            }
        }
        .toSet()

    private fun requiredProperties(schema: JsonNode): Set<String> =
        (sequenceOf(schema) + schema.path("allOf").asSequence())
            .flatMap { node -> node.path("required").asSequence() }
            .map { it.stringValue() }
            .toSet()

    private fun pointer(path: String): String = path.replace("~", "~0").replace("/", "~1")

    private companion object {
        val EXPECTED_PATHS = setOf(
            "/api/v1/auth/oauth2/authorization/kakao",
            "/api/v1/auth/oauth2/callback/kakao",
            "/api/v1/auth/oauth2/exchange",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/users/me",
            "/api/v1/restaurants/search",
            "/api/v1/restaurants/{restaurantId}",
            "/api/v1/restaurants/{restaurantId}/reviews",
            "/api/v1/addresses/search",
            "/api/v1/reviews",
            "/api/v1/users/me/reviews",
            "/api/v1/reviews/{reviewId}",
            "/api/v1/reviews/{reviewId}/reports",
            "/api/v1/restaurants/{restaurantId}/reports",
            "/api/v1/admin/review-reports",
            "/api/v1/admin/review-reports/{reportId}",
            "/api/v1/admin/restaurant-reports",
            "/api/v1/admin/restaurant-reports/{reportId}",
            "/api/v1/admin/restaurants/{restaurantId}/merge",
            "/api/v1/admin/restaurants/{restaurantId}/pickup-location",
            "/api/v1/admin/restaurants/{restaurantId}/pickup-location/verified-address",
            "/api/v1/admin/restaurants/{restaurantId}/name",
            "/api/v1/admin/restaurants/{restaurantId}/status",
            "/api/v1/admin/reviews/{reviewId}",
            "/api/v1/admin/restaurants/search",
            "/api/v1/admin/restaurants/{restaurantId}",
            "/api/v1/admin/moderation-audits",
        )

        val PUBLIC_OPERATIONS = setOf(
            "/api/v1/auth/oauth2/authorization/kakao" to "get",
            "/api/v1/auth/oauth2/callback/kakao" to "get",
            "/api/v1/auth/oauth2/exchange" to "post",
            "/api/v1/auth/refresh" to "post",
            "/api/v1/restaurants/search" to "get",
            "/api/v1/restaurants/{restaurantId}" to "get",
            "/api/v1/restaurants/{restaurantId}/reviews" to "get",
        )

        val BEARER_OPERATIONS = setOf(
            "/api/v1/auth/logout" to "post",
            "/api/v1/users/me" to "get",
            "/api/v1/addresses/search" to "get",
            "/api/v1/reviews" to "post",
            "/api/v1/users/me/reviews" to "get",
            "/api/v1/reviews/{reviewId}" to "patch",
            "/api/v1/reviews/{reviewId}" to "delete",
            "/api/v1/reviews/{reviewId}/reports" to "post",
            "/api/v1/restaurants/{restaurantId}/reports" to "post",
            "/api/v1/admin/review-reports" to "get",
            "/api/v1/admin/review-reports/{reportId}" to "patch",
            "/api/v1/admin/restaurant-reports" to "get",
            "/api/v1/admin/restaurant-reports/{reportId}" to "patch",
            "/api/v1/admin/restaurants/{restaurantId}/merge" to "post",
            "/api/v1/admin/restaurants/{restaurantId}/pickup-location" to "patch",
            "/api/v1/admin/restaurants/{restaurantId}/pickup-location/verified-address" to "patch",
            "/api/v1/admin/restaurants/{restaurantId}/name" to "patch",
            "/api/v1/admin/restaurants/{restaurantId}/status" to "patch",
            "/api/v1/admin/reviews/{reviewId}" to "get",
            "/api/v1/admin/restaurants/search" to "get",
            "/api/v1/admin/restaurants/{restaurantId}" to "get",
            "/api/v1/admin/moderation-audits" to "get",
        )

        val REQUEST_REFS = mapOf(
            ("/api/v1/auth/oauth2/exchange" to "post") to "OAuthExchangeCodeRequest",
            ("/api/v1/auth/refresh" to "post") to "TokenRequest",
            ("/api/v1/auth/logout" to "post") to "TokenRequest",
            ("/api/v1/reviews" to "post") to "CreateReviewRequest",
            ("/api/v1/reviews/{reviewId}" to "patch") to "UpdateReviewRequest",
            ("/api/v1/reviews/{reviewId}/reports" to "post") to "CreateReviewReportRequest",
            ("/api/v1/restaurants/{restaurantId}/reports" to "post") to "CreateRestaurantInfoReportRequest",
            ("/api/v1/admin/review-reports/{reportId}" to "patch") to "ReviewReportDecisionRequest",
            ("/api/v1/admin/restaurant-reports/{reportId}" to "patch") to "RestaurantInfoReportDecisionRequest",
            ("/api/v1/admin/restaurants/{restaurantId}/merge" to "post") to "MergeRestaurantRequest",
            ("/api/v1/admin/restaurants/{restaurantId}/pickup-location" to "patch") to
                "RelinkRestaurantPickupLocationRequest",
            ("/api/v1/admin/restaurants/{restaurantId}/pickup-location/verified-address" to "patch") to
                "RelinkRestaurantVerifiedAddressRequest",
            ("/api/v1/admin/restaurants/{restaurantId}/name" to "patch") to "RenameRestaurantRequest",
            ("/api/v1/admin/restaurants/{restaurantId}/status" to "patch") to "ChangeRestaurantStatusRequest",
        )

        val RESPONSE_REFS = mapOf(
            ("/api/v1/auth/oauth2/exchange" to "post") to ("200" to "AuthTokensResponse"),
            ("/api/v1/auth/refresh" to "post") to ("200" to "AuthTokensResponse"),
            ("/api/v1/users/me" to "get") to ("200" to "UserResponse"),
            ("/api/v1/restaurants/search" to "get") to ("200" to "RestaurantSearchResponse"),
            ("/api/v1/restaurants/{restaurantId}" to "get") to ("200" to "RestaurantDetailResponse"),
            ("/api/v1/restaurants/{restaurantId}/reviews" to "get") to ("200" to "PublicReviewListResponse"),
            ("/api/v1/addresses/search" to "get") to ("200" to "AddressSearchResponse"),
            ("/api/v1/reviews" to "post") to ("201" to "ReviewResponse"),
            ("/api/v1/users/me/reviews" to "get") to ("200" to "MyReviewListResponse"),
            ("/api/v1/reviews/{reviewId}" to "patch") to ("200" to "ReviewResponse"),
            ("/api/v1/reviews/{reviewId}" to "delete") to ("200" to "DeleteReviewResponse"),
            ("/api/v1/reviews/{reviewId}/reports" to "post") to ("201" to "ReviewReportResponse"),
            ("/api/v1/restaurants/{restaurantId}/reports" to "post") to ("201" to "RestaurantInfoReportResponse"),
            ("/api/v1/admin/review-reports" to "get") to ("200" to "PendingReviewReportPageResponse"),
            ("/api/v1/admin/review-reports/{reportId}" to "patch") to ("200" to "ReviewReportResponse"),
            ("/api/v1/admin/restaurant-reports" to "get") to ("200" to "PendingRestaurantInfoReportPageResponse"),
            ("/api/v1/admin/restaurant-reports/{reportId}" to "patch") to ("200" to "RestaurantInfoReportResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}/merge" to "post") to ("200" to "RestaurantMergeResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}/pickup-location" to "patch") to
                ("200" to "RestaurantPickupRelinkResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}/pickup-location/verified-address" to "patch") to
                ("200" to "RestaurantPickupRelinkResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}/name" to "patch") to
                ("200" to "RestaurantRenameResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}/status" to "patch") to
                ("200" to "RestaurantStatusChangeResponse"),
            ("/api/v1/admin/reviews/{reviewId}" to "get") to ("200" to "AdminReviewDetailResponse"),
            ("/api/v1/admin/restaurants/search" to "get") to
                ("200" to "AdminRestaurantSearchPageResponse"),
            ("/api/v1/admin/restaurants/{restaurantId}" to "get") to
                ("200" to "AdminRestaurantDetailResponse"),
            ("/api/v1/admin/moderation-audits" to "get") to ("200" to "ModerationAuditPageResponse"),
        )

        val EXPECTED_SCHEMA_NAMES = setOf(
            "ProblemDetail", "OAuthExchangeCodeRequest", "TokenRequest", "AuthTokensResponse", "UserResponse",
            "RestaurantSearchResponse", "RestaurantSearchCandidateResponse",
            "AddressSearchResponse", "AddressSearchCandidateResponse", "RestaurantDetailResponse",
            "RestaurantPickupLocationResponse", "RestaurantBrandReportResponse",
            "RestaurantPickupLocationReportResponse", "RestaurantBrandReportMetricsResponse",
            "RestaurantPickupLocationReportMetricsResponse", "RestaurantAggregateMetricResponse",
            "CreateReviewRequest", "UpdateReviewRequest", "RestaurantTargetRequest",
            "ExistingRestaurantTargetRequest", "KakaoRestaurantTargetRequest",
            "ManualExistingLocationRestaurantTargetRequest", "ManualAddressRestaurantTargetRequest",
            "ReviewResponse", "ReviewRestaurantResponse", "ReviewRatingsResponse", "MyReviewListResponse",
            "PublicReviewListResponse", "PublicReviewListItemResponse", "PublicReviewAuthorActivityResponse",
            "DeleteReviewResponse", "CreateReviewReportRequest", "CreateRestaurantInfoReportRequest",
            "ReviewReportDecisionRequest", "RestaurantInfoReportDecisionRequest",
            "RestaurantInfoCorrectionRequest", "RenameRestaurantCorrectionRequest",
            "RelinkExistingPickupCorrectionRequest", "RelinkVerifiedAddressCorrectionRequest",
            "MergeRestaurantCorrectionRequest", "CloseRestaurantCorrectionRequest",
            "MergeRestaurantRequest", "RelinkRestaurantPickupLocationRequest", "ReviewReportResponse",
            "RenameRestaurantRequest", "ChangeRestaurantStatusRequest", "RelinkRestaurantVerifiedAddressRequest",
            "RestaurantInfoReportResponse", "PendingReviewReportResponse", "PendingReviewReportPageResponse",
            "PendingRestaurantInfoReportResponse", "PendingRestaurantInfoReportPageResponse",
            "RestaurantMergeResponse", "RestaurantPickupRelinkResponse", "RestaurantRenameResponse",
            "RestaurantStatusChangeResponse", "AdminReviewDetailResponse", "AdminReviewAuthorResponse",
            "AdminReviewRestaurantResponse", "AdminReviewRatingsResponse", "AdminRestaurantSearchPageResponse",
            "AdminRestaurantSearchItemResponse", "AdminRestaurantDetailResponse", "AdminPickupLocationResponse",
            "AdminExternalReferenceResponse", "ModerationAuditPageResponse", "ModerationAuditResponse",
        )

        val ENUM_PROPERTIES = mapOf(
            ("UserResponse" to "role") to setOf("USER", "ADMIN"),
            ("RestaurantSearchResponse" to "externalSearchStatus") to setOf("AVAILABLE", "UNAVAILABLE"),
            ("RestaurantSearchCandidateResponse" to "candidateType") to setOf("INTERNAL", "KAKAO"),
            ("RestaurantSearchCandidateResponse" to "aggregationStatus") to
                setOf("NO_REVIEWS", "COLLECTING", "PUBLISHED"),
            ("RestaurantBrandReportResponse" to "status") to setOf("NO_REVIEWS", "COLLECTING", "PUBLISHED"),
            ("RestaurantPickupLocationReportResponse" to "status") to
                setOf("NO_REVIEWS", "COLLECTING", "PUBLISHED"),
            ("RestaurantTargetRequest" to "type") to
                setOf("EXISTING", "KAKAO", "MANUAL_EXISTING_LOCATION", "MANUAL_ADDRESS"),
            ("ManualExistingLocationRestaurantTargetRequest" to "platforms") to
                setOf("BAEMIN", "COUPANG_EATS", "YOGIYO", "OTHER"),
            ("ManualAddressRestaurantTargetRequest" to "platforms") to
                setOf("BAEMIN", "COUPANG_EATS", "YOGIYO", "OTHER"),
            ("ReviewRatingsResponse" to "packagingStability") to
                setOf("VERY_GOOD", "GOOD", "NEEDS_IMPROVEMENT", "MAJOR_IMPROVEMENT", "NOT_OBSERVED"),
            ("ReviewResponse" to "commentModerationStatus") to
                setOf("NONE", "PENDING", "PUBLISHED", "REJECTED", "HIDDEN_REPORTED"),
            ("ReviewResponse" to "visibilityStatus") to setOf("ACTIVE", "EXCLUDED"),
            ("CreateReviewReportRequest" to "reason") to setOf(
                "PERSONAL_INFORMATION", "ABUSIVE_CONTENT", "IRRELEVANT_CONTENT", "FALSE_INFORMATION", "SPAM", "OTHER",
            ),
            ("CreateRestaurantInfoReportRequest" to "reason") to
                setOf("INCORRECT_NAME", "INCORRECT_PICKUP_LOCATION", "DUPLICATE", "CLOSED", "OTHER"),
            ("ReviewReportDecisionRequest" to "decision") to setOf("DISMISS", "HIDE_COMMENT", "EXCLUDE_REVIEW"),
            ("RestaurantInfoReportDecisionRequest" to "decision") to setOf("DISMISS", "RESOLVE"),
            ("RestaurantInfoCorrectionRequest" to "type") to setOf(
                "RENAME", "RELINK_EXISTING_PICKUP", "RELINK_VERIFIED_ADDRESS", "MERGE", "CLOSE",
            ),
            ("ChangeRestaurantStatusRequest" to "action") to setOf("CLOSE", "REOPEN"),
            ("ReviewReportResponse" to "reason") to setOf(
                "PERSONAL_INFORMATION", "ABUSIVE_CONTENT", "IRRELEVANT_CONTENT", "FALSE_INFORMATION", "SPAM", "OTHER",
            ),
            ("ReviewReportResponse" to "status") to setOf("PENDING", "RESOLVED"),
            ("ReviewReportResponse" to "decision") to setOf("DISMISS", "HIDE_COMMENT", "EXCLUDE_REVIEW"),
            ("RestaurantInfoReportResponse" to "reason") to
                setOf("INCORRECT_NAME", "INCORRECT_PICKUP_LOCATION", "DUPLICATE", "CLOSED", "OTHER"),
            ("RestaurantInfoReportResponse" to "status") to setOf("PENDING", "RESOLVED"),
            ("RestaurantInfoReportResponse" to "decision") to setOf("DISMISS", "RESOLVE"),
            ("PendingReviewReportResponse" to "reason") to setOf(
                "PERSONAL_INFORMATION", "ABUSIVE_CONTENT", "IRRELEVANT_CONTENT", "FALSE_INFORMATION", "SPAM", "OTHER",
            ),
            ("PendingRestaurantInfoReportResponse" to "reason") to
                setOf("INCORRECT_NAME", "INCORRECT_PICKUP_LOCATION", "DUPLICATE", "CLOSED", "OTHER"),
            ("RestaurantMergeResponse" to "status") to setOf("ACTIVE", "CLOSED", "MERGED"),
            ("RestaurantStatusChangeResponse" to "status") to setOf("ACTIVE", "CLOSED", "MERGED"),
            ("RestaurantDetailResponse" to "status") to setOf("ACTIVE", "CLOSED", "MERGED"),
            ("AdminRestaurantSearchItemResponse" to "status") to setOf("ACTIVE", "CLOSED", "MERGED"),
            ("AdminRestaurantDetailResponse" to "status") to setOf("ACTIVE", "CLOSED", "MERGED"),
        )

        val NULLABLE_PROPERTIES = setOf(
            "UserResponse" to "termsVersion", "RestaurantSearchCandidateResponse" to "restaurantId",
            "RestaurantSearchCandidateResponse" to "kakaoPlaceId", "AddressSearchCandidateResponse" to "lotNumberAddress",
            "AddressSearchCandidateResponse" to "existingPickupLocationId",
            "RestaurantPickupLocationResponse" to "detailAddress", "RestaurantBrandReportResponse" to "metrics",
            "RestaurantPickupLocationReportResponse" to "metrics", "CreateReviewRequest" to "comment",
            "UpdateReviewRequest" to "comment", "ManualAddressRestaurantTargetRequest" to "detailAddress",
            "ReviewResponse" to "comment", "MyReviewListResponse" to "nextCursor",
            "PublicReviewListResponse" to "nextCursor", "PublicReviewListItemResponse" to "comment",
            "CreateReviewReportRequest" to "details", "CreateRestaurantInfoReportRequest" to "details",
            "ReviewReportDecisionRequest" to "reason", "RestaurantInfoReportDecisionRequest" to "reason",
            "RestaurantInfoReportDecisionRequest" to "correction",
            "RelinkVerifiedAddressCorrectionRequest" to "detailAddress",
            "MergeRestaurantRequest" to "reason", "RelinkRestaurantPickupLocationRequest" to "reason",
            "RenameRestaurantRequest" to "reason", "ChangeRestaurantStatusRequest" to "reason",
            "RelinkRestaurantVerifiedAddressRequest" to "detailAddress",
            "RelinkRestaurantVerifiedAddressRequest" to "reason",
            "ReviewReportResponse" to "decision", "ReviewReportResponse" to "decidedAt",
            "RestaurantInfoReportResponse" to "decision", "RestaurantInfoReportResponse" to "decidedAt",
            "PendingReviewReportResponse" to "details",
            "PendingReviewReportPageResponse" to "nextCursor", "PendingRestaurantInfoReportResponse" to "details",
            "PendingRestaurantInfoReportPageResponse" to "nextCursor",
            "AdminRestaurantSearchPageResponse" to "nextCursor",
            "AdminRestaurantSearchItemResponse" to "canonicalRestaurantId",
            "AdminRestaurantSearchItemResponse" to "detailAddress",
            "AdminRestaurantDetailResponse" to "canonicalRestaurantId",
            "AdminPickupLocationResponse" to "detailAddress",
            "ModerationAuditPageResponse" to "nextCursor", "ModerationAuditResponse" to "reason",
        )

        val NON_NULL_PROPERTIES = setOf(
            "RestaurantDetailResponse" to "verificationStatus", "RestaurantDetailResponse" to "verificationNotice",
            "PublicReviewListItemResponse" to "verificationStatus",
            "PublicReviewListItemResponse" to "verificationNotice", "ReviewResponse" to "reviewId",
            "ReviewRatingsResponse" to "packagingStability",
            "CreateReviewRequest" to "restaurantTarget", "CreateReviewRequest" to "visitMonth",
            "CreateReviewRequest" to "pickupSpaceCleanliness", "CreateReviewRequest" to "packagingStability",
            "CreateReviewRequest" to "orderReadiness", "CreateReviewRequest" to "handoffAccuracy",
            "CreateReviewRequest" to "staffInteraction", "CreateReviewRequest" to "riderRespect",
            "UpdateReviewRequest" to "pickupSpaceCleanliness", "UpdateReviewRequest" to "packagingStability",
            "UpdateReviewRequest" to "orderReadiness", "UpdateReviewRequest" to "handoffAccuracy",
            "UpdateReviewRequest" to "staffInteraction", "UpdateReviewRequest" to "riderRespect",
            "ManualExistingLocationRestaurantTargetRequest" to "platforms",
            "ManualAddressRestaurantTargetRequest" to "platforms",
            "CreateReviewReportRequest" to "reason", "CreateRestaurantInfoReportRequest" to "reason",
            "ReviewReportDecisionRequest" to "decision",
            "RestaurantInfoReportDecisionRequest" to "decision",
            "MergeRestaurantRequest" to "canonicalRestaurantId",
            "RelinkRestaurantPickupLocationRequest" to "pickupLocationId",
            "RenameRestaurantRequest" to "name", "ChangeRestaurantStatusRequest" to "action",
            "RelinkRestaurantVerifiedAddressRequest" to "addressQuery",
            "RelinkRestaurantVerifiedAddressRequest" to "selectedStandardAddress",
        )

        val NULLABLE_REFERENCE_PROPERTIES = mapOf(
            ("RestaurantBrandReportResponse" to "metrics") to "RestaurantBrandReportMetricsResponse",
            ("RestaurantPickupLocationReportResponse" to "metrics") to
                "RestaurantPickupLocationReportMetricsResponse",
        )

        val TARGET_MAPPINGS = mapOf(
            "EXISTING" to "#/components/schemas/ExistingRestaurantTargetRequest",
            "KAKAO" to "#/components/schemas/KakaoRestaurantTargetRequest",
            "MANUAL_EXISTING_LOCATION" to "#/components/schemas/ManualExistingLocationRestaurantTargetRequest",
            "MANUAL_ADDRESS" to "#/components/schemas/ManualAddressRestaurantTargetRequest",
        )

        val CORRECTION_MAPPINGS = mapOf(
            "RENAME" to "#/components/schemas/RenameRestaurantCorrectionRequest",
            "RELINK_EXISTING_PICKUP" to "#/components/schemas/RelinkExistingPickupCorrectionRequest",
            "RELINK_VERIFIED_ADDRESS" to "#/components/schemas/RelinkVerifiedAddressCorrectionRequest",
            "MERGE" to "#/components/schemas/MergeRestaurantCorrectionRequest",
            "CLOSE" to "#/components/schemas/CloseRestaurantCorrectionRequest",
        )

        val CURSOR_OPERATIONS = setOf(
            "/api/v1/restaurants/{restaurantId}/reviews" to "get",
            "/api/v1/users/me/reviews" to "get",
            "/api/v1/admin/review-reports" to "get",
            "/api/v1/admin/restaurant-reports" to "get",
            "/api/v1/admin/restaurants/search" to "get",
            "/api/v1/admin/moderation-audits" to "get",
        )

        val SEARCH_OPERATIONS = setOf(
            "/api/v1/restaurants/search" to "get",
            "/api/v1/addresses/search" to "get",
            "/api/v1/admin/restaurants/search" to "get",
        )

        val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete", "options", "head", "trace")
    }
}
