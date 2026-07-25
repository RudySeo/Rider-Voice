package com.ridervoice.api.review.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.review.application.PublicReviewListService
import com.ridervoice.api.review.application.model.PublicReviewAuthorActivityResult
import com.ridervoice.api.review.application.model.PublicReviewListItemResult
import com.ridervoice.api.review.application.model.PublicReviewListResult
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListPublicRestaurantReviewsUseCase
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(controllers = [PublicReviewController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    ReviewHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    PublicReviewApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class PublicReviewApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var listPublicReviews: ListPublicRestaurantReviewsUseCase

    @Test
    fun `public review history is anonymous and includes author activity and unverified notice`() {
        `when`(listPublicReviews.list(ListPublicRestaurantReviewsCommand(10L, null, 20)))
            .thenReturn(PublicReviewListResult(listOf(item()), null))

        mockMvc.get("/api/v1/restaurants/10/reviews").andExpect {
            status { isOk() }
            jsonPath("$.items[0].reviewId") { value(100) }
            jsonPath("$.items[0].visitMonth") { value("2026-07") }
            jsonPath("$.items[0].current") { value(false) }
            jsonPath("$.items[0].ratings.packagingStability") { value("VERY_GOOD") }
            jsonPath("$.items[0].comment") { value("공개 의견") }
            jsonPath("$.items[0].authorActivity.activityMonths") { value(3) }
            jsonPath("$.items[0].authorActivity.publicReviewCount") { value(8) }
            jsonPath("$.items[0].authorActivity.authorId") { doesNotExist() }
            jsonPath("$.items[0].authorActivity.publicAuthorId") { doesNotExist() }
            jsonPath("$.items[0].authorActivity.nickname") { doesNotExist() }
            jsonPath("$.items[0].verificationStatus") { value("UNVERIFIED") }
            jsonPath("$.items[0].verificationNotice") { value(PublicReviewListService.VERIFICATION_NOTICE) }
            jsonPath("$.nextCursor") { value(null) }
        }

        verify(listPublicReviews).list(ListPublicRestaurantReviewsCommand(10L, null, 20))
    }

    @Test
    fun `public list validates path pagination and malformed cursor`() {
        mockMvc.get("/api/v1/restaurants/0/reviews").andExpect { status { isBadRequest() } }
        mockMvc.get("/api/v1/restaurants/10/reviews") { param("size", "0") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/api/v1/restaurants/10/reviews") { param("size", "51") }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/api/v1/restaurants/10/reviews") { param("cursor", "not-a-cursor") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `OpenAPI exposes anonymous public review list contract`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}/reviews'].get.security") {
                doesNotExist()
            }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}/reviews'].get.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/PublicReviewListResponse")
            }
            jsonPath("$.components.schemas.PublicReviewListItemResponse.properties.verificationStatus.enum") {
                value(containsInAnyOrder("UNVERIFIED"))
            }
            jsonPath("$.components.schemas.PublicReviewListItemResponse.properties.verificationNotice") { exists() }
            jsonPath("$.components.schemas.PublicReviewAuthorActivityResponse.properties") {
                value(org.hamcrest.Matchers.aMapWithSize<String, Any>(2))
            }
            jsonPath("$.components.schemas.PublicReviewAuthorActivityResponse.properties.authorId") {
                doesNotExist()
            }
            jsonPath("$.components.schemas.PublicReviewAuthorActivityResponse.properties.nickname") {
                doesNotExist()
            }
        }
    }

    private fun item() = PublicReviewListItemResult(
        reviewId = 100L,
        visitMonth = VisitMonth.parse("2026-07"),
        current = false,
        ratings = ReviewRatings(
            pickupSpaceCleanliness = ReviewRating.GOOD,
            packagingStability = ReviewRating.VERY_GOOD,
            orderReadiness = ReviewRating.GOOD,
            handoffAccuracy = ReviewRating.GOOD,
            staffInteraction = ReviewRating.NOT_OBSERVED,
            riderRespect = ReviewRating.GOOD,
        ),
        comment = "공개 의견",
        authorActivity = PublicReviewAuthorActivityResult(3, 8L),
        createdAt = Instant.parse("2026-07-25T03:00:00Z"),
        verificationStatus = "UNVERIFIED",
        verificationNotice = PublicReviewListService.VERIFICATION_NOTICE,
    )
}
