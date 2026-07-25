package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.common.config.OpenApiConfiguration
import com.ridervoice.api.common.error.GlobalExceptionHandler
import com.ridervoice.api.common.security.AccessTokenAuthenticator
import com.ridervoice.api.common.security.AuthenticatedUserPrincipal
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportPageResult
import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.PendingReviewCommentPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewCommentResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportResult
import com.ridervoice.api.moderation.application.model.ReportModerationCursor
import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.ReviewCommentDecisionResult
import com.ridervoice.api.moderation.application.model.ReviewReportResult
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewCommentCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewCommentUseCase
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewCommentsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewCommentsUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsUseCase
import com.ridervoice.api.moderation.domain.CommentModerationDecision
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.review.domain.ReviewCommentStatus
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

@WebMvcTest(controllers = [ModerationReportController::class, AdminModerationController::class])
@Import(
    OpenApiConfiguration::class,
    GlobalExceptionHandler::class,
    ModerationHttpMapper::class,
    SecurityConfig::class,
    OpaqueAccessTokenAuthenticationFilter::class,
    SecurityProblemHandler::class,
    ModerationApiContractMockMvcTest.EnableSecurityConfiguration::class,
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class ModerationApiContractMockMvcTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    class EnableSecurityConfiguration {
        @Bean
        fun accessTokenAuthenticator(): AccessTokenAuthenticator = AccessTokenAuthenticator { null }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createReviewReport: CreateReviewReportUseCase

    @MockitoBean
    private lateinit var createRestaurantReport: CreateRestaurantInfoReportUseCase

    @MockitoBean
    private lateinit var listComments: ListPendingReviewCommentsUseCase

    @MockitoBean
    private lateinit var decideComment: DecideReviewCommentUseCase

    @MockitoBean
    private lateinit var listReviewReports: ListPendingReviewReportsUseCase

    @MockitoBean
    private lateinit var decideReviewReport: DecideReviewReportUseCase

    @MockitoBean
    private lateinit var listRestaurantReports: ListPendingRestaurantInfoReportsUseCase

    @MockitoBean
    private lateinit var decideRestaurantReport: DecideRestaurantInfoReportUseCase

    @Test
    fun `report and admin endpoints reject public access`() {
        mockMvc.post("/api/v1/reviews/40/reports") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"SPAM","details":null}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }
        mockMvc.get("/api/v1/admin/review-comments").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
        }
        verifyNoInteractions(createReviewReport, listComments)
    }

    @Test
    fun `USER can submit both report types but cannot access ADMIN queues or decisions`() {
        val reviewCommand = CreateReviewReportCommand(USER_ID, 40L, ReviewReportReason.SPAM, "반복 게시")
        val restaurantCommand = CreateRestaurantInfoReportCommand(
            USER_ID,
            50L,
            RestaurantInfoReportReason.DUPLICATE,
            "중복 음식점",
        )
        `when`(createReviewReport.createReviewReport(reviewCommand)).thenReturn(reviewReport())
        `when`(createRestaurantReport.createRestaurantInfoReport(restaurantCommand)).thenReturn(restaurantReport())

        mockMvc.post("/api/v1/reviews/40/reports") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"SPAM","details":"반복 게시"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.reportId") { value(101) }
            jsonPath("$.reviewId") { value(40) }
            jsonPath("$.reason") { value("SPAM") }
            jsonPath("$.status") { value("PENDING") }
        }
        mockMvc.post("/api/v1/restaurants/50/reports") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"DUPLICATE","details":"중복 음식점"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.reportId") { value(202) }
            jsonPath("$.restaurantId") { value(50) }
        }
        verify(createReviewReport).createReviewReport(reviewCommand)
        verify(createRestaurantReport).createRestaurantInfoReport(restaurantCommand)

        listOf(
            "/api/v1/admin/review-comments",
            "/api/v1/admin/review-reports",
            "/api/v1/admin/restaurant-reports",
        ).forEach { path ->
            mockMvc.get(path) { with(userAuthentication()) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
        }
        mockMvc.patch("/api/v1/admin/review-comments/40") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"decision":"APPROVE"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `ADMIN can list and decide every moderation queue but cannot submit USER reports`() {
        `when`(listComments.list(ListPendingReviewCommentsQuery(ADMIN_ID, null, 20))).thenReturn(
            PendingReviewCommentPageResult(
                listOf(PendingReviewCommentResult(40L, 9L, "검수 의견", NOW, NOW)),
                CommentModerationCursor(NOW, 40L),
            ),
        )
        `when`(listReviewReports.list(ListPendingReviewReportsQuery(ADMIN_ID, null, 20))).thenReturn(
            PendingReviewReportPageResult(
                listOf(PendingReviewReportResult(101L, USER_ID, 40L, ReviewReportReason.SPAM, "반복 게시", NOW)),
                ReportModerationCursor(NOW, 101L),
            ),
        )
        `when`(
            listRestaurantReports.list(ListPendingRestaurantInfoReportsQuery(ADMIN_ID, null, 20)),
        ).thenReturn(
            PendingRestaurantInfoReportPageResult(
                listOf(
                    PendingRestaurantInfoReportResult(
                        202L,
                        USER_ID,
                        50L,
                        RestaurantInfoReportReason.DUPLICATE,
                        "중복 음식점",
                        NOW,
                    ),
                ),
                ReportModerationCursor(NOW, 202L),
            ),
        )
        `when`(
            decideComment.decide(DecideReviewCommentCommand(ADMIN_ID, 40L, CommentModerationDecision.APPROVE)),
        ).thenReturn(ReviewCommentDecisionResult(40L, ReviewCommentStatus.PUBLISHED, NOW))
        `when`(
            decideReviewReport.decideReviewReport(
                DecideReviewReportCommand(ADMIN_ID, 101L, ReviewReportDecision.EXCLUDE_REVIEW, "도배 확인"),
            ),
        ).thenReturn(reviewReport().copy(status = ReportStatus.RESOLVED, decision = ReviewReportDecision.EXCLUDE_REVIEW, decidedAt = NOW))
        `when`(
            decideRestaurantReport.decideRestaurantInfoReport(
                DecideRestaurantInfoReportCommand(
                    ADMIN_ID,
                    202L,
                    RestaurantInfoReportDecision.RESOLVE,
                    "정보 정정",
                ),
            ),
        ).thenReturn(
            restaurantReport().copy(
                status = ReportStatus.RESOLVED,
                decision = RestaurantInfoReportDecision.RESOLVE,
                decidedAt = NOW,
            ),
        )

        mockMvc.get("/api/v1/admin/review-comments") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].reviewId") { value(40) }
            jsonPath("$.nextCursor") { isString() }
        }
        mockMvc.get("/api/v1/admin/review-reports") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].reporterUserId") { value(USER_ID) }
            jsonPath("$.items[0].reason") { value("SPAM") }
        }
        mockMvc.get("/api/v1/admin/restaurant-reports") { with(adminAuthentication()) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].restaurantId") { value(50) }
            jsonPath("$.items[0].reason") { value("DUPLICATE") }
        }
        mockMvc.patch("/api/v1/admin/review-comments/40") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"decision":"APPROVE"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.commentModerationStatus") { value("PUBLISHED") }
        }
        mockMvc.patch("/api/v1/admin/review-reports/101") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"decision":"EXCLUDE_REVIEW","reason":"도배 확인"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("EXCLUDE_REVIEW") }
        }
        mockMvc.patch("/api/v1/admin/restaurant-reports/202") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"decision":"RESOLVE","reason":"정보 정정"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("RESOLVE") }
        }

        mockMvc.post("/api/v1/reviews/40/reports") {
            with(adminAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"SPAM"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `request enums pagination and malformed cursors return stable ProblemDetail`() {
        mockMvc.post("/api/v1/reviews/40/reports") {
            with(userAuthentication())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"UNKNOWN"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("BAD_REQUEST") }
        }
        mockMvc.get("/api/v1/admin/review-reports") {
            with(adminAuthentication())
            param("size", "51")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
        mockMvc.get("/api/v1/admin/restaurant-reports") {
            with(adminAuthentication())
            param("cursor", "not-a-cursor")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BAD_REQUEST") }
        }
    }

    @Test
    fun `opaque report cursor and size map to the ADMIN queue query`() {
        val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "$NOW|101".toByteArray(StandardCharsets.UTF_8),
        )
        val query = ListPendingReviewReportsQuery(ADMIN_ID, ReportModerationCursor(NOW, 101L), 10)
        `when`(listReviewReports.list(query)).thenReturn(PendingReviewReportPageResult(emptyList(), null))

        mockMvc.get("/api/v1/admin/review-reports") {
            with(adminAuthentication())
            param("cursor", cursor)
            param("size", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items") { isEmpty() }
            jsonPath("$.nextCursor") { value(null) }
        }

        verify(listReviewReports).list(query)
    }

    @Test
    fun `OpenAPI exposes report reason decision cursor and ProblemDetail contracts`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/reviews/{reviewId}/reports'].post.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/restaurants/{restaurantId}/reports'].post.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/admin/review-comments'].get.security[0].bearerAuth") { isArray() }
            jsonPath("$.paths['/api/v1/admin/review-reports'].get.parameters[0].name") { value("cursor") }
            jsonPath("$.paths['/api/v1/admin/restaurant-reports'].get.parameters[1].schema.maximum") { value(50) }
            jsonPath("$.paths['/api/v1/admin/review-reports/{reportId}'].patch.responses['409'].content['application/problem+json'].schema['\$ref']") {
                value("#/components/schemas/ProblemDetail")
            }
            jsonPath("$.components.schemas.CreateReviewReportRequest.properties.reason.enum") {
                value(
                    containsInAnyOrder(
                        "PERSONAL_INFORMATION",
                        "ABUSIVE_CONTENT",
                        "IRRELEVANT_CONTENT",
                        "FALSE_INFORMATION",
                        "SPAM",
                        "OTHER",
                    ),
                )
            }
            jsonPath("$.components.schemas.CreateRestaurantInfoReportRequest.properties.reason.enum") {
                value(
                    containsInAnyOrder(
                        "INCORRECT_NAME",
                        "INCORRECT_PICKUP_LOCATION",
                        "DUPLICATE",
                        "CLOSED",
                        "OTHER",
                    ),
                )
            }
            jsonPath("$.components.schemas.CommentDecisionRequest.properties.decision.enum") {
                value(containsInAnyOrder("APPROVE", "REJECT"))
            }
            jsonPath("$.components.schemas.ReviewReportDecisionRequest.properties.decision.enum") {
                value(containsInAnyOrder("DISMISS", "HIDE_COMMENT", "EXCLUDE_REVIEW"))
            }
            jsonPath("$.components.schemas.RestaurantInfoReportDecisionRequest.properties.decision.enum") {
                value(containsInAnyOrder("DISMISS", "RESOLVE"))
            }
            jsonPath("$.components.schemas.PendingReviewReportPageResponse.properties.nextCursor") { exists() }
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

    private fun reviewReport() = ReviewReportResult(
        reportId = 101L,
        reviewId = 40L,
        reason = ReviewReportReason.SPAM,
        status = ReportStatus.PENDING,
        decision = null,
        createdAt = NOW,
        decidedAt = null,
    )

    private fun restaurantReport() = RestaurantInfoReportResult(
        reportId = 202L,
        restaurantId = 50L,
        reason = RestaurantInfoReportReason.DUPLICATE,
        status = ReportStatus.PENDING,
        decision = null,
        createdAt = NOW,
        decidedAt = null,
    )

    private companion object {
        const val USER_ID = 7L
        const val ADMIN_ID = 8L
        val NOW: Instant = Instant.parse("2026-07-26T03:00:00Z")
    }
}
