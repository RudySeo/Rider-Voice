package com.ridervoice.api.moderation.application

import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportUseCase
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportUseCase
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportReason
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@SpringBootTest
class MvpModerationConcurrencyIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var users: UserStore
    @Autowired private lateinit var pickupLocations: PickupLocationRepository
    @Autowired private lateinit var restaurants: RestaurantRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var createReviewReport: CreateReviewReportUseCase
    @Autowired private lateinit var createRestaurantReport: CreateRestaurantInfoReportUseCase
    @Autowired private lateinit var dataSource: DataSource

    private val userIds = linkedSetOf<Long>()
    private val pickupLocationIds = linkedSetOf<Long>()
    private val restaurantIds = linkedSetOf<Long>()
    private val reviewIds = linkedSetOf<Long>()

    @AfterEach
    fun cleanOwnedFixtures() {
        val jdbc = JdbcTemplate(dataSource)
        userIds.forEach { userId ->
            jdbc.update("delete from moderation_audits where actor_user_id = ?", userId)
            jdbc.update("delete from review_reports where reporter_user_id = ?", userId)
            jdbc.update("delete from restaurant_info_reports where reporter_user_id = ?", userId)
        }
        reviewIds.forEach { reviewId ->
            jdbc.update("delete from review_reports where review_id = ?", reviewId)
            jdbc.update("delete from reviews where id = ?", reviewId)
        }
        restaurantIds.forEach { restaurantId ->
            jdbc.update("delete from restaurant_info_reports where restaurant_id = ?", restaurantId)
            jdbc.update("delete from restaurants where id = ?", restaurantId)
        }
        pickupLocationIds.forEach { pickupLocationId ->
            jdbc.update("delete from pickup_locations where id = ?", pickupLocationId)
        }
        userIds.forEach { userId -> jdbc.update("delete from users where id = ?", userId) }
    }

    @Test
    fun `concurrent duplicate review report requests retain one unique report`() {
        val fixture = reportFixture()
        val command = CreateReviewReportCommand(
            reporterUserId = fixture.user.id,
            reviewId = fixture.review.id,
            reason = ReviewReportReason.SPAM,
            details = null,
        )

        val results = race { createReviewReport.createReviewReport(command) }

        assertOneSuccessAndOneConflict(results)
        assertThat(
            JdbcTemplate(dataSource).queryForObject(
                "select count(*) from review_reports where reporter_user_id = ? and review_id = ?",
                Long::class.java,
                fixture.user.id,
                fixture.review.id,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `concurrent duplicate restaurant report requests retain one unique report`() {
        val fixture = reportFixture()
        val command = CreateRestaurantInfoReportCommand(
            reporterUserId = fixture.user.id,
            restaurantId = fixture.review.restaurant.id,
            reason = RestaurantInfoReportReason.DUPLICATE,
            details = null,
        )

        val results = race { createRestaurantReport.createRestaurantInfoReport(command) }

        assertOneSuccessAndOneConflict(results)
        assertThat(
            JdbcTemplate(dataSource).queryForObject(
                "select count(*) from restaurant_info_reports where reporter_user_id = ? and restaurant_id = ?",
                Long::class.java,
                fixture.user.id,
                fixture.review.restaurant.id,
            ),
        ).isEqualTo(1L)
    }

    private fun reportFixture(): ReportFixture {
        val user = activeUser(UserRole.USER)
        val location = locationFixture("report-race")
        val restaurant = restaurantFixture("신고 대상", location)
        val review = reviews.save(
            Review(
                author = user,
                restaurant = restaurant,
                visitMonth = VisitMonth.parse("2026-07"),
                ratings = ratings(),
                comment = null,
            ),
        ).also { reviewIds += it.id }
        return ReportFixture(user, review)
    }

    private fun activeUser(role: UserRole): User = User().also {
        User::class.java.getDeclaredField("role").also { field ->
            field.isAccessible = true
            field.set(it, role)
        }
    }.let(users::saveUser).also { userIds += it.id }

    private fun locationFixture(label: String): PickupLocation = pickupLocations.save(
        PickupLocation(
            standardAddress = "서울 강남구 $label-${UUID.randomUUID()}",
            detailAddress = null,
            latitude = BigDecimal("37.50000000"),
            longitude = BigDecimal("127.00000000"),
            source = PickupLocationSource.MANUAL_ADDRESS,
        ),
    ).also { pickupLocationIds += it.id }

    private fun restaurantFixture(name: String, location: PickupLocation): Restaurant =
        restaurants.save(Restaurant("$name-${UUID.randomUUID()}", location)).also {
            restaurantIds += it.id
        }

    private fun ratings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun <T> race(action: (Int) -> T): List<Result<T>> {
        val ready = CountDownLatch(CONCURRENT_REQUESTS)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        return try {
            val futures = (0 until CONCURRENT_REQUESTS).map { index ->
                executor.submit<Result<T>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "Concurrent test start timed out" }
                    runCatching { action(index) }
                }
            }
            check(ready.await(5, TimeUnit.SECONDS)) { "Concurrent workers did not become ready" }
            start.countDown()
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertOneSuccessAndOneConflict(results: List<Result<*>>) {
        val failures = results.mapNotNull { it.exceptionOrNull() }
            .joinToString { "${it::class.simpleName}: ${it.message}" }
        assertThat(results.count(Result<*>::isSuccess)).withFailMessage(failures).isEqualTo(1)
        assertThat(results.count(Result<*>::isFailure)).withFailMessage(failures).isEqualTo(1)
        assertThat(results.single(Result<*>::isFailure).exceptionOrNull())
            .isInstanceOf(StateConflictException::class.java)
    }

    private data class ReportFixture(
        val user: User,
        val review: Review,
    )

    private companion object {
        const val CONCURRENT_REQUESTS = 2
    }
}
