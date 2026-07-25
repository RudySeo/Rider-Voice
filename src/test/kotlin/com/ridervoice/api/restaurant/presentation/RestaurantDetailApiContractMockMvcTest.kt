package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.restaurant.application.PublicRestaurantDetailService
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult
import com.ridervoice.api.restaurant.application.model.PublicRestaurantPickupLocationResult
import com.ridervoice.api.restaurant.application.model.RestaurantAggregateMetricResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportResult
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportMetrics
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportResult
import com.ridervoice.api.restaurant.application.port.`in`.GetPublicRestaurantDetailUseCase
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
import java.math.BigDecimal

@WebMvcTest(controllers = [RestaurantDetailController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    RestaurantDetailHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    RestaurantDetailApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class RestaurantDetailApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getRestaurantDetail: GetPublicRestaurantDetailUseCase

    @Test
    fun `restaurant detail is public and always returns canonical data with verification notice`() {
        `when`(getRestaurantDetail.get(10L)).thenReturn(detailResult())

        mockMvc.get("/api/v1/restaurants/10").andExpect {
            status { isOk() }
            jsonPath("$.restaurantId") { value(20) }
            jsonPath("$.name") { value("대표 브랜드") }
            jsonPath("$.pickupLocation.pickupLocationId") { value(30) }
            jsonPath("$.pickupLocation.standardAddress") { value("서울 강남구 테헤란로 1") }
            jsonPath("$.pickupLocation.detailAddress") { value("지하 1층 픽업대") }
            jsonPath("$.brandReport.status") { value("COLLECTING") }
            jsonPath("$.brandReport.contributorCount") { value(4) }
            jsonPath("$.brandReport.metrics") { value(null) }
            jsonPath("$.pickupLocationReport.status") { value("PUBLISHED") }
            jsonPath("$.pickupLocationReport.contributorCount") { value(5) }
            jsonPath("$.pickupLocationReport.metrics.pickupSpaceCleanliness.observedCount") { value(4) }
            jsonPath("$.pickupLocationReport.metrics.pickupSpaceCleanliness.notObservedCount") { value(1) }
            jsonPath("$.pickupLocationReport.metrics.pickupSpaceCleanliness.distribution.VERY_GOOD") {
                value(50.0)
            }
            jsonPath("$.verificationStatus") { value("UNVERIFIED") }
            jsonPath("$.verificationNotice") { value(PublicRestaurantDetailService.VERIFICATION_NOTICE) }
            jsonPath("$.pickupLocation.restaurants") { doesNotExist() }
            jsonPath("$.pickupLocation.brands") { doesNotExist() }
            jsonPath("$.siblingBrands") { doesNotExist() }
        }
    }

    @Test
    fun `unknown restaurant returns stable not found ProblemDetail`() {
        `when`(getRestaurantDetail.get(404L)).thenThrow(ResourceNotFoundException("Restaurant not found"))

        mockMvc.get("/api/v1/restaurants/404").andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    @Test
    fun `OpenAPI exposes public detail reports and verification contract`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}'].get.security") { doesNotExist() }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}'].get.parameters[0].schema.format") {
                value("int64")
            }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}'].get.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/RestaurantDetailResponse")
            }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}'].get.responses['404'].content['application/problem+json']") {
                exists()
            }
            jsonPath("$.components.schemas.RestaurantDetailResponse.properties.verificationStatus.enum") {
                value(containsInAnyOrder("UNVERIFIED"))
            }
            jsonPath("$.components.schemas.RestaurantDetailResponse.properties.verificationNotice") { exists() }
            jsonPath("$.components.schemas.RestaurantDetailResponse.properties.pickupLocation") { exists() }
            jsonPath("$.components.schemas.RestaurantDetailResponse.properties.brandReport") { exists() }
            jsonPath("$.components.schemas.RestaurantDetailResponse.properties.pickupLocationReport") { exists() }
            jsonPath("$.components.schemas.RestaurantPickupLocationResponse.properties.restaurants") { doesNotExist() }
            jsonPath("$.components.schemas.RestaurantPickupLocationResponse.properties.brands") { doesNotExist() }
            jsonPath("$.components.schemas.RestaurantBrandReportResponse.properties.status.enum") {
                value(containsInAnyOrder("NO_REVIEWS", "COLLECTING", "PUBLISHED"))
            }
        }
    }

    private fun detailResult(): PublicRestaurantDetailResult {
        val metric = RestaurantAggregateMetricResult(
            observedCount = 4,
            notObservedCount = 1,
            distribution = linkedMapOf(
                "VERY_GOOD" to BigDecimal("50.0"),
                "GOOD" to BigDecimal("25.0"),
                "NEEDS_IMPROVEMENT" to BigDecimal("25.0"),
                "MAJOR_IMPROVEMENT" to BigDecimal("0.0"),
            ),
        )
        return PublicRestaurantDetailResult(
            restaurantId = 20L,
            name = "대표 브랜드",
            pickupLocation = PublicRestaurantPickupLocationResult(
                pickupLocationId = 30L,
                standardAddress = "서울 강남구 테헤란로 1",
                detailAddress = "지하 1층 픽업대",
                latitude = BigDecimal("37.50000000"),
                longitude = BigDecimal("127.00000000"),
            ),
            brandReport = RestaurantBrandReportResult(AggregationStatus.COLLECTING, 4, null),
            pickupLocationReport = RestaurantPickupLocationReportResult(
                AggregationStatus.PUBLISHED,
                5,
                RestaurantPickupLocationReportMetrics(metric, metric, metric),
            ),
            verificationStatus = "UNVERIFIED",
            verificationNotice = PublicRestaurantDetailService.VERIFICATION_NOTICE,
        )
    }
}
