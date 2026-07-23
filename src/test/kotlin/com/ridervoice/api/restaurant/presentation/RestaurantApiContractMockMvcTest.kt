package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OnboardingPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.restaurant.application.model.RegisterRestaurantCommand
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateResult
import com.ridervoice.api.restaurant.application.model.RestaurantRegistrationResult
import com.ridervoice.api.restaurant.application.model.RestaurantSearchQuery
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(controllers = [RestaurantController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    RestaurantApiContractMockMvcTest.AuthenticationTestConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
@EnableWebSecurity
class RestaurantApiContractMockMvcTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var restaurantUseCase: RestaurantUseCase

    @BeforeEach
    fun setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `restaurant endpoints require authentication`() {
        mockMvc.get("/api/v1/restaurants/search") {
            param("query", "강남 분식")
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }

        mockMvc.post("/api/v1/restaurants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"강남 분식","kakaoPlaceId":"1234567890"}"""
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }
    }

    @Test
    fun `restaurant endpoints reject a principal without ROLE_USER`() {
        mockMvc.get("/api/v1/restaurants/search") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ONBOARDING_TOKEN")
            param("query", "강남 분식")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/restaurants") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ONBOARDING_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"강남 분식","kakaoPlaceId":"1234567890"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    @Test
    fun `search maps the validated HTTP query and application result`() {
        val restaurantId = UUID.fromString("1db6c773-9111-44a1-adb1-8243a2536078")
        `when`(restaurantUseCase.search(RestaurantSearchQuery("강남 분식"))).thenReturn(
            RestaurantSearchResult(
                candidates = listOf(
                    RestaurantCandidateResult(
                        restaurantId = restaurantId,
                        kakaoPlaceId = "1234567890",
                        name = "라이더보이스 강남점",
                        address = "서울 강남구 테헤란로 1",
                        latitude = BigDecimal("37.4987654"),
                        longitude = BigDecimal("127.0276543"),
                    ),
                    RestaurantCandidateResult(
                        restaurantId = null,
                        kakaoPlaceId = "9876543210",
                        name = "새로운 분식집",
                        address = "서울 강남구 역삼로 2",
                        latitude = BigDecimal("37.5000000"),
                        longitude = BigDecimal("127.0300000"),
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/restaurants/search") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $USER_ACCESS_TOKEN")
            param("query", "강남 분식")
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.candidates.length()") { value(2) }
            jsonPath("$.candidates[0].restaurantId") { value(restaurantId.toString()) }
            jsonPath("$.candidates[0].kakaoPlaceId") { value("1234567890") }
            jsonPath("$.candidates[0].name") { value("라이더보이스 강남점") }
            jsonPath("$.candidates[0].address") { value("서울 강남구 테헤란로 1") }
            jsonPath("$.candidates[0].latitude") { value(37.4987654) }
            jsonPath("$.candidates[0].longitude") { value(127.0276543) }
            jsonPath("$.candidates[1].restaurantId") { doesNotExist() }
        }

        verify(restaurantUseCase).search(RestaurantSearchQuery("강남 분식"))
    }

    @Test
    fun `registration maps the validated request to a command and maps the result`() {
        val restaurantId = UUID.fromString("1db6c773-9111-44a1-adb1-8243a2536078")
        val command = RegisterRestaurantCommand(
            query = "강남 분식",
            kakaoPlaceId = "1234567890",
        )
        `when`(restaurantUseCase.register(command)).thenReturn(
            RestaurantRegistrationResult(
                restaurantId = restaurantId,
                kakaoPlaceId = "1234567890",
                name = "라이더보이스 강남점",
                address = "서울 강남구 테헤란로 1",
                latitude = BigDecimal("37.4987654"),
                longitude = BigDecimal("127.0276543"),
            ),
        )

        mockMvc.post("/api/v1/restaurants") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $USER_ACCESS_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"강남 분식","kakaoPlaceId":"1234567890"}"""
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.restaurantId") { value(restaurantId.toString()) }
            jsonPath("$.kakaoPlaceId") { value("1234567890") }
            jsonPath("$.name") { value("라이더보이스 강남점") }
            jsonPath("$.address") { value("서울 강남구 테헤란로 1") }
            jsonPath("$.latitude") { value(37.4987654) }
            jsonPath("$.longitude") { value(127.0276543) }
        }

        verify(restaurantUseCase).register(command)
    }

    @Test
    fun `blank query and Kakao place id return validation problem details`() {
        mockMvc.get("/api/v1/restaurants/search") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $USER_ACCESS_TOKEN")
            param("query", " ")
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:validation-failed") }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }

        mockMvc.post("/api/v1/restaurants") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $USER_ACCESS_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":" ","kakaoPlaceId":""}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:validation-failed") }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `unverified selected place returns a sanitized not found problem detail`() {
        val command = RegisterRestaurantCommand("강남 분식", "missing-place")
        `when`(restaurantUseCase.register(command))
            .thenThrow(ResourceNotFoundException("provider result did not contain missing-place"))

        mockMvc.post("/api/v1/restaurants") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $USER_ACCESS_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"강남 분식","kakaoPlaceId":"missing-place"}"""
        }.andExpect {
            status { isNotFound() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:ridervoice:error:resource-not-found") }
            jsonPath("$.status") { value(404) }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            jsonPath("$.detail") { value("The requested resource was not found.") }
        }
    }

    @Test
    fun `generated OpenAPI documents restaurant purpose security validation and schemas`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/restaurants/search'].get.summary") { value("음식점 검색") }
            jsonPath("$.paths['/api/v1/restaurants/search'].get.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].name") { value("query") }
            jsonPath("$.paths['/api/v1/restaurants/search'].get.parameters[0].required") { value(true) }
            jsonPath("$.paths['/api/v1/restaurants/search'].get.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/RestaurantSearchResponse")
            }
            jsonPath("$.paths['/api/v1/restaurants'].post.summary") { value("선택한 카카오 장소 등록") }
            jsonPath("$.paths['/api/v1/restaurants'].post.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/restaurants'].post.requestBody.content['application/json'].schema['\$ref']") {
                value("#/components/schemas/CreateRestaurantRequest")
            }
            jsonPath("$.paths['/api/v1/restaurants'].post.responses['200'].content['application/json'].schema['\$ref']") {
                value("#/components/schemas/RestaurantRegistrationResponse")
            }
            jsonPath("$.components.schemas.CreateRestaurantRequest.required") {
                value(org.hamcrest.Matchers.containsInAnyOrder("query", "kakaoPlaceId"))
            }
            jsonPath("$.components.schemas.CreateRestaurantRequest.properties.query.minLength") { value(1) }
            jsonPath("$.components.schemas.CreateRestaurantRequest.properties.kakaoPlaceId.minLength") { value(1) }
            jsonPath("$.components.schemas.RestaurantCandidateResponse.properties.restaurantId.type") {
                value(org.hamcrest.Matchers.containsInAnyOrder("string", "null"))
            }
        }
    }

    private companion object {
        const val USER_ACCESS_TOKEN = "valid-user-access-token"
        const val ONBOARDING_TOKEN = "valid-onboarding-token"
        val TEST_USER_ID: UUID = UUID.fromString("8cc310ff-f4b7-44a7-bf4d-fd865d555d6f")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AuthenticationTestConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { accessToken ->
            when (accessToken) {
                USER_ACCESS_TOKEN -> AuthenticatedUserPrincipal(TEST_USER_ID)
                ONBOARDING_TOKEN -> OnboardingPrincipal(TEST_USER_ID)
                else -> null
            }
        }
    }
}
