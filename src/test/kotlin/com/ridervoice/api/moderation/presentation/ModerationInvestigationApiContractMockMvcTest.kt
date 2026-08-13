package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.moderation.application.model.AdminRestaurantDetailResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchItemResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchPageResult
import com.ridervoice.api.moderation.application.model.AdminReviewDetailResult
import com.ridervoice.api.moderation.application.model.ModerationAuditPageResult
import com.ridervoice.api.moderation.application.model.ModerationAuditResult
import com.ridervoice.api.moderation.application.port.`in`.GetAdminRestaurantDetailQuery
import com.ridervoice.api.moderation.application.port.`in`.GetAdminRestaurantDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailQuery
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsUseCase
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsQuery
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsUseCase
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(controllers = [AdminInvestigationController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    ModerationInvestigationHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    ModerationInvestigationApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration
class ModerationInvestigationApiContractMockMvcTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var reviewDetail: GetAdminReviewDetailUseCase
    @MockitoBean private lateinit var restaurantSearch: SearchAdminRestaurantsUseCase
    @MockitoBean private lateinit var restaurantDetail: GetAdminRestaurantDetailUseCase
    @MockitoBean private lateinit var audits: ListModerationAuditsUseCase

    @Test
    fun `ADMIN can inspect review restaurant and audit context without OAuth identifiers`() {
        `when`(reviewDetail.get(GetAdminReviewDetailQuery(ADMIN_ID, 40L))).thenReturn(review())
        `when`(restaurantDetail.get(GetAdminRestaurantDetailQuery(ADMIN_ID, 20L))).thenReturn(restaurant())
        `when`(restaurantSearch.search(SearchAdminRestaurantsQuery(ADMIN_ID, "브랜드", null, null, 20)))
            .thenReturn(AdminRestaurantSearchPageResult(listOf(restaurantItem()), null))
        `when`(audits.list(ListModerationAuditsQuery(ADMIN_ID, null, null, null, null, null, 20))).thenReturn(
            ModerationAuditPageResult(listOf(audit()), null),
        )

        mockMvc.get("/api/v1/admin/reviews/40") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.reviewId") { value(40) }
            jsonPath("$.ratings.packagingStability") { value("GOOD") }
            jsonPath("$.comment") { value("신고 조사 원문") }
            jsonPath("$.oauthSubject") { doesNotExist() }
        }
        mockMvc.get("/api/v1/admin/restaurants/search?query=브랜드") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].status") { value("ACTIVE") }
        }
        mockMvc.get("/api/v1/admin/restaurants/20") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.pickupLocation.pickupLocationId") { value(30) }
        }
        mockMvc.get("/api/v1/admin/moderation-audits") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].action") { value("RESTAURANT_RENAMED") }
        }
    }

    @Test
    fun `investigation endpoints reject unauthenticated callers`() {
        mockMvc.get("/api/v1/admin/reviews/40").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    private fun adminAuthentication() = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUserPrincipal(ADMIN_ID, AuthenticatedUserPrincipal.ADMIN_AUTHORITY),
            null,
            listOf(SimpleGrantedAuthority(AuthenticatedUserPrincipal.ADMIN_AUTHORITY)),
        ),
    )

    private fun ratings() = ReviewRatings(
        ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD,
        ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD,
    )

    private fun review() = AdminReviewDetailResult(
        40L, 9L, UserStatus.ACTIVE, 3, 4, 20L, "브랜드", RestaurantStatus.ACTIVE,
        30L, "서울 강남구 1", VisitMonth.parse("2026-07"), ratings(), "신고 조사 원문",
        ReviewCommentStatus.PENDING, ReviewVisibilityStatus.ACTIVE, true,
        null, NOW, NOW,
    )

    private fun restaurantItem() = AdminRestaurantSearchItemResult(
        20L, "브랜드", RestaurantStatus.ACTIVE, 30L, "서울 강남구 1", null, NOW,
    )

    private fun restaurant() = AdminRestaurantDetailResult(
        20L, "브랜드", RestaurantStatus.ACTIVE, 30L, "서울 강남구 1", null,
        BigDecimal("37.5"), BigDecimal("127.0"), null, emptySet(), 1, NOW, NOW,
    )

    private fun audit() = ModerationAuditResult(
        1L, ADMIN_ID, ModerationAuditAction.RESTAURANT_RENAMED, ModerationTargetType.RESTAURANT,
        20L, "상호 정정", "before", "after", NOW, NOW,
    )

    private companion object {
        const val ADMIN_ID = 8L
        val NOW: Instant = Instant.parse("2026-07-26T00:00:00Z")
    }
}
