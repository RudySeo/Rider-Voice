package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.moderation.application.model.RestaurantMergeResult
import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationUseCase
import com.ridervoice.api.restaurant.domain.RestaurantStatus
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(controllers = [AdminRestaurantController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    RestaurantAdministrationHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    RestaurantAdministrationApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class RestaurantAdministrationApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var mergeRestaurant: MergeRestaurantUseCase
    @MockitoBean private lateinit var relinkRestaurant: RelinkRestaurantPickupLocationUseCase

    @Test
    fun `restaurant administration endpoints require ADMIN`() {
        mockMvc.post("/api/v1/admin/restaurants/10/merge") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"canonicalRestaurantId":20}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.patch("/api/v1/admin/restaurants/10/pickup-location") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"pickupLocationId":200}"""
        }.andExpect { status { isForbidden() } }

        verifyNoInteractions(mergeRestaurant, relinkRestaurant)
    }

    @Test
    fun `ADMIN merges a duplicate and relinks a pickup location`() {
        `when`(
            mergeRestaurant.merge(MergeRestaurantCommand(ADMIN_ID, 10L, 20L, "중복 확인")),
        ).thenReturn(RestaurantMergeResult(10L, RestaurantStatus.MERGED, 20L, NOW))
        `when`(
            relinkRestaurant.relinkPickupLocation(
                RelinkRestaurantPickupLocationCommand(ADMIN_ID, 20L, 200L, "주소 정정"),
            ),
        ).thenReturn(RestaurantPickupRelinkResult(20L, 200L, NOW))

        mockMvc.post("/api/v1/admin/restaurants/10/merge") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"canonicalRestaurantId":20,"reason":"중복 확인"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.restaurantId") { value(10) }
            jsonPath("$.status") { value("MERGED") }
            jsonPath("$.canonicalRestaurantId") { value(20) }
        }

        mockMvc.patch("/api/v1/admin/restaurants/20/pickup-location") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"pickupLocationId":200,"reason":"주소 정정"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.restaurantId") { value(20) }
            jsonPath("$.pickupLocationId") { value(200) }
        }
    }

    @Test
    fun `invalid ids use validation ProblemDetail and OpenAPI exposes both contracts`() {
        mockMvc.post("/api/v1/admin/restaurants/10/merge") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"canonicalRestaurantId":0}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }

        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/admin/restaurants/{restaurantId}/merge'].post.security[0].bearerAuth") {
                isArray()
            }
            jsonPath("$.paths['/api/v1/admin/restaurants/{restaurantId}/pickup-location'].patch.security[0].bearerAuth") {
                isArray()
            }
            jsonPath("$.components.schemas.MergeRestaurantRequest.required") {
                isArray()
            }
            jsonPath("$.components.schemas.RestaurantMergeResponse.properties.canonicalRestaurantId.format") {
                value("int64")
            }
        }
    }

    private fun userAuthentication() = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUserPrincipal(USER_ID),
            null,
            listOf(SimpleGrantedAuthority(AuthenticatedUserPrincipal.USER_AUTHORITY)),
        ),
    )

    private fun adminAuthentication() = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUserPrincipal(ADMIN_ID, AuthenticatedUserPrincipal.ADMIN_AUTHORITY),
            null,
            listOf(SimpleGrantedAuthority(AuthenticatedUserPrincipal.ADMIN_AUTHORITY)),
        ),
    )

    private companion object {
        const val USER_ID = 7L
        const val ADMIN_ID = 8L
        val NOW: Instant = Instant.parse("2026-07-26T00:00:00Z")
    }
}
