package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.restaurant.application.model.AddressSearchCandidate
import com.ridervoice.api.restaurant.application.model.AddressSearchResult
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
import com.ridervoice.api.restaurant.application.model.RestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@WebMvcTest(controllers = [RestaurantSearchController::class, AddressSearchController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    RestaurantSearchHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    RestaurantSearchApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class RestaurantSearchApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { accessToken ->
            accessToken.takeIf { it == "valid-access-token" }
                ?.let { AuthenticatedUserPrincipal(TEST_USER_ID) }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var searchRestaurants: SearchRestaurantsUseCase

    @MockitoBean
    private lateinit var searchAddresses: SearchAddressesUseCase

    @Test
    fun `restaurant search is public and maps internal and Kakao candidates`() {
        `when`(searchRestaurants.search(SearchRestaurantsCommand("강남 분식"))).thenReturn(
            RestaurantSearchResult(
                externalSearchStatus = ExternalSearchStatus.AVAILABLE,
                candidates = listOf(
                    RestaurantSearchCandidate(
                        candidateType = RestaurantCandidateType.INTERNAL,
                        restaurantId = 10L,
                        externalPlaceId = "kakao-10",
                        name = "내부 브랜드",
                        address = "서울 강남구 테헤란로 1",
                        aggregationStatus = AggregationStatus.COLLECTING,
                        contributorCount = 3,
                    ),
                    RestaurantSearchCandidate(
                        candidateType = RestaurantCandidateType.KAKAO,
                        restaurantId = null,
                        externalPlaceId = "kakao-20",
                        name = "외부 후보",
                        address = "서울 강남구 역삼로 1",
                        aggregationStatus = AggregationStatus.NO_REVIEWS,
                        contributorCount = 0,
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/restaurants/search") {
            param("query", "강남 분식")
        }.andExpect {
            status { isOk() }
            jsonPath("$.externalSearchStatus") { value("AVAILABLE") }
            jsonPath("$.candidates.length()") { value(2) }
            jsonPath("$.candidates[0].candidateType") { value("INTERNAL") }
            jsonPath("$.candidates[0].restaurantId") { value(10) }
            jsonPath("$.candidates[0].kakaoPlaceId") { value("kakao-10") }
            jsonPath("$.candidates[0].aggregationStatus") { value("COLLECTING") }
            jsonPath("$.candidates[0].contributorCount") { value(3) }
            jsonPath("$.candidates[1].candidateType") { value("KAKAO") }
            jsonPath("$.candidates[1].restaurantId") { value(null) }
            jsonPath("$.candidates[1].kakaoPlaceId") { value("kakao-20") }
        }
    }

    @Test
    fun `address search requires USER and carries authenticated user ID`() {
        val query = "서울 강남구 테헤란로 1"
        `when`(searchAddresses.search(SearchAddressesCommand(TEST_USER_ID, query))).thenReturn(
            AddressSearchResult(
                query = query,
                candidates = listOf(
                    AddressSearchCandidate(
                        standardAddress = query,
                        lotNumberAddress = "서울 강남구 역삼동 1",
                        latitude = BigDecimal("37.12345678"),
                        longitude = BigDecimal("127.12345678"),
                        existingPickupLocationId = 20L,
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/addresses/search") {
            param("query", query)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }

        mockMvc.get("/api/v1/addresses/search") {
            param("query", query)
            with(
                authentication(
                    UsernamePasswordAuthenticationToken(
                        AuthenticatedUserPrincipal(TEST_USER_ID),
                        null,
                        listOf(SimpleGrantedAuthority(AuthenticatedUserPrincipal.USER_AUTHORITY)),
                    ),
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.query") { value(query) }
            jsonPath("$.candidates[0].standardAddress") { value(query) }
            jsonPath("$.candidates[0].lotNumberAddress") { value("서울 강남구 역삼동 1") }
            jsonPath("$.candidates[0].latitude") { value(37.12345678) }
            jsonPath("$.candidates[0].longitude") { value(127.12345678) }
            jsonPath("$.candidates[0].existingPickupLocationId") { value(20) }
        }
    }

    @Test
    fun `search query is required and constrained to 2 through 100 characters`() {
        listOf("", "가", "  가  ", "가".repeat(101)).forEach { query ->
            mockMvc.get("/api/v1/restaurants/search") {
                param("query", query)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }

        mockMvc.get("/api/v1/restaurants/search").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }

        verifyNoInteractions(searchRestaurants)
    }

    @Test
    fun `generated OpenAPI exposes public restaurant and USER address search contracts`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.security") { doesNotExist() }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].name") { value("query") }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].required") { value(true) }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].schema.minLength") { value(2) }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].schema.maxLength") { value(100) }
                jsonPath("$.paths['/api/v1/restaurants/search'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/RestaurantSearchResponse")
                }
                jsonPath("$.paths['/api/v1/addresses/search'].get.security[0].bearerAuth") { isArray() }
                jsonPath("$.paths['/api/v1/addresses/search'].get.responses['200'].content['application/json'].schema['\$ref']") {
                    value("#/components/schemas/AddressSearchResponse")
                }
                jsonPath("$.components.schemas.RestaurantSearchResponse.properties.externalSearchStatus.enum") {
                    value(containsInAnyOrder("AVAILABLE", "UNAVAILABLE"))
                }
                jsonPath("$.components.schemas.RestaurantSearchResponse.properties.candidates.maxItems") { value(20) }
                jsonPath("$.components.schemas.RestaurantSearchCandidateResponse.properties.candidateType.enum") {
                    value(containsInAnyOrder("INTERNAL", "KAKAO"))
                }
                jsonPath("$.components.schemas.RestaurantSearchCandidateResponse.properties.restaurantId.format") {
                    value("int64")
                }
                jsonPath("$.components.schemas.RestaurantSearchCandidateResponse.properties.kakaoPlaceId") { exists() }
                jsonPath("$.components.schemas.RestaurantSearchCandidateResponse.properties.aggregationStatus.enum") {
                    value(containsInAnyOrder("NO_REVIEWS", "COLLECTING", "PUBLISHED"))
                }
                jsonPath("$.components.schemas.AddressSearchCandidateResponse.properties.existingPickupLocationId.format") {
                    value("int64")
                }
                jsonPath("$.paths['/api/v1/restaurants']") { doesNotExist() }
            }
    }

    private companion object {
        const val TEST_USER_ID = 42L
    }
}
