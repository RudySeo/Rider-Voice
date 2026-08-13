package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.model.RestaurantRenameResult
import com.ridervoice.api.moderation.application.model.RestaurantStatusChangeResult
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusCommand
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusUseCase
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressUseCase
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
    @MockitoBean private lateinit var relinkRestaurant: RelinkRestaurantPickupLocationUseCase
    @MockitoBean private lateinit var renameRestaurant: RenameRestaurantUseCase
    @MockitoBean private lateinit var changeRestaurantStatus: ChangeRestaurantStatusUseCase
    @MockitoBean private lateinit var relinkVerifiedAddress: RelinkRestaurantVerifiedAddressUseCase

    @Test
    fun `restaurant administration endpoints require ADMIN`() {
        mockMvc.patch("/api/v1/admin/restaurants/10/pickup-location") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"pickupLocationId":200}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.patch("/api/v1/admin/restaurants/10/pickup-location") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"pickupLocationId":200}"""
        }.andExpect { status { isForbidden() } }

        verifyNoInteractions(relinkRestaurant)
    }

    @Test
    fun `ADMIN relinks a pickup location`() {
        `when`(
            relinkRestaurant.relinkPickupLocation(
                RelinkRestaurantPickupLocationCommand(ADMIN_ID, 20L, 200L, "주소 정정"),
            ),
        ).thenReturn(RestaurantPickupRelinkResult(20L, 200L, NOW))

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
    fun `ADMIN renames closes and reopens a restaurant`() {
        `when`(
            renameRestaurant.rename(RenameRestaurantCommand(ADMIN_ID, 20L, "새 브랜드", "상호 정정")),
        ).thenReturn(RestaurantRenameResult(20L, "새 브랜드", NOW))
        `when`(
            changeRestaurantStatus.changeStatus(ChangeRestaurantStatusCommand.close(ADMIN_ID, 20L, "폐업")),
        ).thenReturn(RestaurantStatusChangeResult(20L, RestaurantStatus.CLOSED, NOW))

        mockMvc.patch("/api/v1/admin/restaurants/20/name") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"새 브랜드","reason":"상호 정정"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.restaurantId") { value(20) }
            jsonPath("$.name") { value("새 브랜드") }
        }

        mockMvc.patch("/api/v1/admin/restaurants/20/status") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"action":"CLOSE","reason":"폐업"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
        }
    }

    @Test
    fun `ADMIN relinks to a server-verified new address`() {
        `when`(
            relinkVerifiedAddress.relinkVerifiedAddress(
                RelinkRestaurantVerifiedAddressCommand(
                    ADMIN_ID, 20L, "서울 강남구 새 주소", "서울 강남구 새 주소 1", "지하 1층", "주소 정정",
                ),
            ),
        ).thenReturn(RestaurantPickupRelinkResult(20L, 300L, NOW))

        mockMvc.patch("/api/v1/admin/restaurants/20/pickup-location/verified-address") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"addressQuery":"서울 강남구 새 주소","selectedStandardAddress":"서울 강남구 새 주소 1","detailAddress":"지하 1층","reason":"주소 정정"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.pickupLocationId") { value(300) }
        }
    }

    @Test
    fun `invalid ids use validation ProblemDetail and OpenAPI exposes both contracts`() {
        mockMvc.patch("/api/v1/admin/restaurants/10/pickup-location") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"pickupLocationId":0}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }

        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/admin/restaurants/{restaurantId}/pickup-location'].patch.security[0].bearerAuth") {
                isArray()
            }
            jsonPath("$.components.schemas.RelinkRestaurantPickupLocationRequest.required") {
                isArray()
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
