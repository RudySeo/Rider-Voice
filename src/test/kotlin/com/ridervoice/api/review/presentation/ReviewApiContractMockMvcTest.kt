package com.ridervoice.api.review.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.review.application.model.MyReviewListResult
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.model.ReviewRestaurantSummary
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.CreateReviewUseCase
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewResult
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(controllers = [ReviewController::class, MyReviewController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    ReviewHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    ReviewApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class ReviewApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { accessToken ->
            accessToken.takeIf { it == VALID_TOKEN }
                ?.let { AuthenticatedUserPrincipal(TEST_USER_ID) }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createReview: CreateReviewUseCase

    @MockitoBean
    private lateinit var updateReview: UpdateReviewUseCase

    @MockitoBean
    private lateinit var deleteReview: DeleteReviewUseCase

    @MockitoBean
    private lateinit var listMyReviews: ListMyReviewsUseCase

    @Test
    fun `review owner endpoints require USER authentication`() {
        mockMvc.post("/api/v1/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = validCreateBody(EXISTING_TARGET)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }

        mockMvc.get("/api/v1/users/me/reviews").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }

        mockMvc.patch("/api/v1/reviews/100") {
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateBody()
        }.andExpect { status { isUnauthorized() } }

        mockMvc.delete("/api/v1/reviews/100").andExpect { status { isUnauthorized() } }

        verifyNoInteractions(createReview, updateReview, deleteReview, listMyReviews)
    }

    @Test
    fun `create maps all four target subtypes and returns a response DTO`() {
        val targets = listOf(
            EXISTING_TARGET to ExistingRestaurantTargetCommand(10L),
            KAKAO_TARGET to KakaoRestaurantTargetCommand("강남 분식", "kakao-10"),
            MANUAL_EXISTING_LOCATION_TARGET to ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = 20L,
                name = "새 브랜드",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
            MANUAL_ADDRESS_TARGET to ManualAddressRestaurantTargetCommand(
                addressQuery = "서울 강남구 테헤란로 1",
                selectedStandardAddress = "서울 강남구 테헤란로 1",
                detailAddress = "지하 1층",
                name = "새 브랜드",
                platforms = setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )

        targets.forEach { (target, expectedTarget) ->
            val expectedCommand = createCommand(expectedTarget)
            `when`(createReview.create(expectedCommand)).thenReturn(reviewResult())

            mockMvc.post("/api/v1/reviews") {
                with(authenticatedUser())
                contentType = MediaType.APPLICATION_JSON
                content = validCreateBody(target)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.reviewId") { value(100) }
                jsonPath("$.restaurant.restaurantId") { value(10) }
                jsonPath("$.visitMonth") { value("2026-07") }
                jsonPath("$.ratings.pickupSpaceCleanliness") { value("GOOD") }
                jsonPath("$.commentModerationStatus") { value("PUBLISHED") }
                jsonPath("$.historyStatus") { doesNotExist() }
                jsonPath("$.sequence") { doesNotExist() }
                jsonPath("$.createdAt") { value("2026-07-25T03:00:00Z") }
            }

            verify(createReview).create(expectedCommand)
        }
    }

    @Test
    fun `Bean Validation rejects missing ratings invalid month long trimmed comment and invalid target fields`() {
        val invalidBodies = listOf(
            validCreateBody(EXISTING_TARGET).replace("\"pickupSpaceCleanliness\":\"GOOD\",", ""),
            validCreateBody(EXISTING_TARGET).replace("2026-07", "2026-13"),
            validCreateBody(EXISTING_TARGET).replace(
                "  즉시 공개할 의견  ",
                " ${"가".repeat(201)} ",
            ),
            validCreateBody("""{"type":"EXISTING","restaurantId":0}"""),
            validCreateBody("""{"type":"KAKAO","query":" ","kakaoPlaceId":""}"""),
            validCreateBody("""{"type":"KAKAO","query":"가","kakaoPlaceId":"kakao-10"}"""),
            validCreateBody(
                """{"type":"MANUAL_EXISTING_LOCATION","pickupLocationId":0,"name":" ","platforms":[]}""",
            ),
            validCreateBody(
                """{"type":"MANUAL_ADDRESS","addressQuery":" ","selectedStandardAddress":"","name":" ","platforms":[]}""",
            ),
            validCreateBody(
                """{"type":"MANUAL_ADDRESS","addressQuery":"가","selectedStandardAddress":"서울","name":"브랜드","platforms":[]}""",
            ),
        )

        invalidBodies.forEach { body ->
            mockMvc.post("/api/v1/reviews") {
                with(authenticatedUser())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }

        verifyNoInteractions(createReview)

        listOf(
            validUpdateBody().replace("\"riderRespect\":\"GOOD\",", ""),
            validUpdateBody().replace("\"comment\":null", "\"comment\":\"${"가".repeat(201)}\""),
        ).forEach { body ->
            mockMvc.patch("/api/v1/reviews/100") {
                with(authenticatedUser())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }

        verifyNoInteractions(updateReview)
    }

    @Test
    fun `ninety day conflict is returned as stable 409 ProblemDetail`() {
        `when`(createReview.create(createCommand(ExistingRestaurantTargetCommand(10L))))
            .thenThrow(StateConflictException("A new review can be submitted 90 days after the last submission"))

        mockMvc.post("/api/v1/reviews") {
            with(authenticatedUser())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateBody(EXISTING_TARGET)
        }.andExpect {
            status { isConflict() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:state-conflict") }
            jsonPath("$.status") { value(409) }
            jsonPath("$.code") { value("STATE_CONFLICT") }
            jsonPath("$.detail") { value("The request conflicts with the current resource state.") }
        }
    }

    @Test
    fun `update delete and cursor list map owner commands and responses`() {
        val result = reviewResult()
        val expectedUpdateCommand = UpdateReviewCommand(TEST_USER_ID, 100L, updatedRatings(), null)
        `when`(updateReview.update(expectedUpdateCommand)).thenReturn(result)
        `when`(deleteReview.delete(DeleteReviewCommand(TEST_USER_ID, 100L))).thenReturn(DeleteReviewResult(100L))
        `when`(listMyReviews.list(ListMyReviewsCommand(TEST_USER_ID, null, 1))).thenReturn(
            MyReviewListResult(
                items = listOf(result),
                nextCursor = ReviewCursor(result.createdAt, result.reviewId),
            ),
        )

        mockMvc.patch("/api/v1/reviews/100") {
            with(authenticatedUser())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateBody()
        }.andExpect {
            status { isOk() }
            jsonPath("$.reviewId") { value(100) }
            jsonPath("$.visitMonth") { value("2026-07") }
        }

        verify(updateReview).update(expectedUpdateCommand)

        mockMvc.delete("/api/v1/reviews/100") {
            with(authenticatedUser())
        }.andExpect {
            status { isOk() }
            jsonPath("$.reviewId") { value(100) }
        }

        mockMvc.get("/api/v1/users/me/reviews") {
            with(authenticatedUser())
            param("size", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].reviewId") { value(100) }
            jsonPath("$.nextCursor") { isString() }
        }
    }

    @Test
    fun `other owner and history reviews are concealed as stable 404`() {
        val command = UpdateReviewCommand(TEST_USER_ID, 999L, updatedRatings(), null)
        `when`(updateReview.update(command))
            .thenThrow(ResourceNotFoundException("Review was not found"))

        mockMvc.patch("/api/v1/reviews/999") {
            with(authenticatedUser())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateBody()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            jsonPath("$.detail") { value("The requested resource was not found.") }
        }
    }

    @Test
    fun `list validates pagination and malformed cursors`() {
        listOf("0", "51").forEach { size ->
            mockMvc.get("/api/v1/users/me/reviews") {
                with(authenticatedUser())
                param("size", size)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }

        mockMvc.get("/api/v1/users/me/reviews") {
            with(authenticatedUser())
            param("cursor", "not-a-cursor")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BAD_REQUEST") }
        }
    }

    @Test
    fun `generated OpenAPI exposes review target discriminator and USER contracts`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/reviews'].post.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/users/me/reviews'].get.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/reviews/{reviewId}'].patch.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/reviews/{reviewId}'].delete.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/reviews'].post.requestBody.content['application/json'].schema['\$ref']") {
                value("#/components/schemas/CreateReviewRequest")
            }
            jsonPath("$.paths['/api/v1/reviews'].post.responses['201'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/ReviewResponse")
            }
            jsonPath("$.paths['/api/v1/users/me/reviews'].get.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/MyReviewListResponse")
            }
            jsonPath("$.paths['/api/v1/reviews/{reviewId}'].patch.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/ReviewResponse")
            }
            jsonPath("$.paths['/api/v1/reviews/{reviewId}'].delete.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/DeleteReviewResponse")
            }
            jsonPath("$.components.schemas.RestaurantTargetRequest.discriminator.propertyName") { value("type") }
            jsonPath("$.components.schemas.RestaurantTargetRequest.discriminator.mapping.EXISTING") {
                value("#/components/schemas/ExistingRestaurantTargetRequest")
            }
            jsonPath("$.components.schemas.RestaurantTargetRequest.discriminator.mapping.KAKAO") {
                value("#/components/schemas/KakaoRestaurantTargetRequest")
            }
            jsonPath("$.components.schemas.RestaurantTargetRequest.discriminator.mapping.MANUAL_EXISTING_LOCATION") {
                value("#/components/schemas/ManualExistingLocationRestaurantTargetRequest")
            }
            jsonPath("$.components.schemas.RestaurantTargetRequest.discriminator.mapping.MANUAL_ADDRESS") {
                value("#/components/schemas/ManualAddressRestaurantTargetRequest")
            }
            jsonPath("$.components.schemas.RestaurantTargetRequest.oneOf.length()") { value(4) }
            jsonPath("$.components.schemas.KakaoRestaurantTargetRequest.allOf[1].properties.query.minLength") { value(2) }
            jsonPath("$.components.schemas.KakaoRestaurantTargetRequest.allOf[1].properties.query.maxLength") { value(100) }
            jsonPath("$.components.schemas.CreateReviewRequest.required") {
                value(
                    containsInAnyOrder(
                        "restaurantTarget",
                        "visitMonth",
                        "pickupSpaceCleanliness",
                        "packagingStability",
                        "orderReadiness",
                        "handoffAccuracy",
                        "staffInteraction",
                        "riderRespect",
                    ),
                )
            }
            jsonPath("$.components.schemas.CreateReviewRequest.properties.visitMonth.pattern") {
                value("^\\d{4}-(0[1-9]|1[0-2])$")
            }
            jsonPath("$.components.schemas.CreateReviewRequest.properties.comment.maxLength") { value(200) }
            jsonPath("$.components.schemas.ReviewRatingsResponse.properties.pickupSpaceCleanliness.enum") {
                value(
                    containsInAnyOrder(
                        "VERY_GOOD",
                        "GOOD",
                        "NEEDS_IMPROVEMENT",
                        "MAJOR_IMPROVEMENT",
                        "NOT_OBSERVED",
                    ),
                )
            }
        }
    }

    private fun reviewResult() = ReviewResult(
        reviewId = 100L,
        restaurant = ReviewRestaurantSummary(
            restaurantId = 10L,
            name = "라이더보이스 강남점",
            address = "서울 강남구 테헤란로 1",
        ),
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ratings(),
        comment = "즉시 공개할 의견",
        commentModerationStatus = ReviewCommentStatus.PUBLISHED,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        createdAt = Instant.parse("2026-07-25T03:00:00Z"),
        updatedAt = Instant.parse("2026-07-25T03:00:00Z"),
    )

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun updatedRatings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.VERY_GOOD,
        packagingStability = ReviewRating.GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun createCommand(target: com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand) =
        CreateReviewCommand(
            authorUserId = TEST_USER_ID,
            restaurantTarget = target,
            visitMonth = VisitMonth.parse("2026-07"),
            ratings = ratings(),
            comment = "  즉시 공개할 의견  ",
        )

    private fun authenticatedUser() = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUserPrincipal(TEST_USER_ID),
            null,
            listOf(SimpleGrantedAuthority(AuthenticatedUserPrincipal.USER_AUTHORITY)),
        ),
    )

    private fun validCreateBody(target: String) = """
        {
          "restaurantTarget":$target,
          "visitMonth":"2026-07",
          "pickupSpaceCleanliness":"GOOD",
          "packagingStability":"VERY_GOOD",
          "orderReadiness":"GOOD",
          "handoffAccuracy":"GOOD",
          "staffInteraction":"NOT_OBSERVED",
          "riderRespect":"GOOD",
          "comment":"  즉시 공개할 의견  "
        }
    """.trimIndent()

    private fun validUpdateBody() = """
        {
          "pickupSpaceCleanliness":"VERY_GOOD",
          "packagingStability":"GOOD",
          "orderReadiness":"GOOD",
          "handoffAccuracy":"GOOD",
          "staffInteraction":"NOT_OBSERVED",
          "riderRespect":"GOOD",
          "comment":null
        }
    """.trimIndent()

    private companion object {
        const val TEST_USER_ID = 42L
        const val VALID_TOKEN = "valid-access-token"
        const val EXISTING_TARGET = """{"type":"EXISTING","restaurantId":10}"""
        const val KAKAO_TARGET = """{"type":"KAKAO","query":"강남 분식","kakaoPlaceId":"kakao-10"}"""
        const val MANUAL_EXISTING_LOCATION_TARGET =
            """{"type":"MANUAL_EXISTING_LOCATION","pickupLocationId":20,"name":"새 브랜드","platforms":["BAEMIN"]}"""
        const val MANUAL_ADDRESS_TARGET =
            """{"type":"MANUAL_ADDRESS","addressQuery":"서울 강남구 테헤란로 1","selectedStandardAddress":"서울 강남구 테헤란로 1","detailAddress":"지하 1층","name":"새 브랜드","platforms":["COUPANG_EATS"]}"""
    }
}
