package com.ridervoice.api.review.application

import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.restaurant.application.RestaurantTargetResolutionService
import com.ridervoice.api.restaurant.application.RestaurantTargetWriter
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ResolveValidatedRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.ReviewResult
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Tag("integration")
class ReviewCoreIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var users: UserStore
    @Autowired private lateinit var pickupLocations: PickupLocationRepository
    @Autowired private lateinit var restaurants: RestaurantRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var targetWriter: RestaurantTargetWriter
    @Autowired private lateinit var targetResolver: ResolveValidatedRestaurantTargetUseCase
    @Autowired private lateinit var updateReview: UpdateReviewUseCase
    @Autowired private lateinit var deleteReview: DeleteReviewUseCase
    @Autowired private lateinit var listMyReviews: ListMyReviewsUseCase
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val authorIds = ConcurrentHashMap.newKeySet<Long>()
    private val restaurantIds = ConcurrentHashMap.newKeySet<Long>()
    private val pickupLocationIds = ConcurrentHashMap.newKeySet<Long>()

    @AfterEach
    fun removeCommittedFixtures() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            authorIds.forEach { authorId -> jdbcTemplate.update("delete from reviews where author_user_id = ?", authorId) }
            restaurantIds.forEach { restaurantId ->
                jdbcTemplate.update("delete from restaurant_platforms where restaurant_id = ?", restaurantId)
                jdbcTemplate.update("delete from restaurants where id = ?", restaurantId)
            }
            pickupLocationIds.forEach { pickupLocationId ->
                jdbcTemplate.update("delete from pickup_locations where id = ?", pickupLocationId)
            }
            authorIds.forEach { authorId -> jdbcTemplate.update("delete from users where id = ?", authorId) }
        }
        authorIds.clear()
        restaurantIds.clear()
        pickupLocationIds.clear()
    }

    @Test
    fun `existing Kakao and both manual targets create one active review each`() {
        val fixture = restaurantFixture("target")
        val author = userFixture()
        val kakaoCandidate = ExternalRestaurantCandidate(
            kakaoPlaceId = "review-kakao-${UUID.randomUUID()}",
            name = "카카오 회귀 브랜드",
            standardAddress = uniqueAddress("kakao"),
            lotNumberAddress = null,
            latitude = BigDecimal("37.51000000"),
            longitude = BigDecimal("127.01000000"),
        )
        val manualAddressCandidate = ExternalAddressCandidate(
            standardAddress = uniqueAddress("manual-address"),
            lotNumberAddress = null,
            latitude = BigDecimal("37.52000000"),
            longitude = BigDecimal("127.02000000"),
        )
        val service = createService(
            keywordCandidates = mapOf("카카오 검색" to kakaoCandidate),
            addressCandidates = mapOf("수동 주소 검색" to manualAddressCandidate),
        )
        val targets = listOf(
            ExistingRestaurantTargetCommand(fixture.restaurant.id),
            KakaoRestaurantTargetCommand("카카오 검색", kakaoCandidate.kakaoPlaceId),
            ManualExistingLocationRestaurantTargetCommand(
                fixture.location.id,
                "기존 장소 새 브랜드",
                setOf(DeliveryPlatform.BAEMIN),
            ),
            ManualAddressRestaurantTargetCommand(
                "수동 주소 검색",
                manualAddressCandidate.standardAddress,
                "지하 픽업대",
                "수동 주소 새 브랜드",
                setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )

        val results = targets.map { target ->
            service.create(createCommand(author.id, target)).also { trackRestaurant(it.restaurant.restaurantId) }
        }

        assertThat(results.map { it.restaurant.restaurantId }).doesNotHaveDuplicates()
        assertThat(results.map { it.reviewId }).allMatch { reviewId ->
            jdbcTemplate.queryForObject(
                "select current_slot from reviews where id = ?",
                Int::class.java,
                reviewId,
            ) == 1
        }
    }

    @Test
    fun `concurrent first submissions retain one active review`() {
        val fixture = restaurantFixture("first-review-race")
        val author = userFixture()
        val service = createService()
        val command = createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id))

        val results = race { service.create(command) }

        assertOneSuccessAndOneConflict(results)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reviews where author_user_id = ? and restaurant_id = ? and current_slot = 1",
                Long::class.java,
                author.id,
                fixture.restaurant.id,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `active review blocks another submission even after ninety days`() {
        val fixture = restaurantFixture("active-block")
        val author = userFixture()
        val service = createService()
        val command = createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id))
        val first = service.create(command)
        backdate(first.reviewId, days = 180)

        assertThatThrownBy { service.create(command) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("active review")
    }

    @Test
    fun `soft delete hides review and permits a new review only after ninety days from creation`() {
        val fixture = restaurantFixture("delete-cooldown")
        val author = userFixture()
        val service = createService()
        val command = createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id))
        val first = service.create(command)

        deleteReview.delete(DeleteReviewCommand(author.id, first.reviewId))

        assertThat(listMyReviews.list(ListMyReviewsCommand(author.id, null, 10)).items).isEmpty()
        val deleted = jdbcTemplate.queryForMap(
            "select current_slot, deleted_at from reviews where id = ?",
            first.reviewId,
        )
        assertThat(deleted["current_slot"]).isNull()
        assertThat(deleted["deleted_at"]).isNotNull()
        assertThatThrownBy { service.create(command) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("90 days")

        backdate(first.reviewId, days = 91)
        val second = service.create(command)

        assertThat(second.reviewId).isNotEqualTo(first.reviewId)
        assertThat(listMyReviews.list(ListMyReviewsCommand(author.id, null, 10)).items.map { it.reviewId })
            .containsExactly(second.reviewId)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reviews where author_user_id = ? and restaurant_id = ?",
                Long::class.java,
                author.id,
                fixture.restaurant.id,
            ),
        ).isEqualTo(2L)
    }

    @Test
    fun `only active review can be updated and visit month remains immutable`() {
        val fixture = restaurantFixture("owner-lifecycle")
        val author = userFixture()
        val service = createService()
        val first = service.create(createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id)))

        val updated = updateReview.update(
            UpdateReviewCommand(author.id, first.reviewId, changedRatings(), "  즉시 공개  "),
        )
        assertThat(updated.visitMonth).isEqualTo(VISIT_MONTH)
        assertThat(updated.ratings).isEqualTo(changedRatings())
        assertThat(updated.comment).isEqualTo("즉시 공개")
        assertThat(updated.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)

        deleteReview.delete(DeleteReviewCommand(author.id, first.reviewId))
        assertThatThrownBy {
            updateReview.update(UpdateReviewCommand(author.id, first.reviewId, ratings(), null))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun createService(
        keywordCandidates: Map<String, ExternalRestaurantCandidate> = emptyMap(),
        addressCandidates: Map<String, ExternalAddressCandidate> = emptyMap(),
    ): ReviewCreateService {
        val validator = RestaurantTargetResolutionService(
            keywordSearch = KakaoKeywordSearchPort { query, _ ->
                ProviderSearchResult.Available(keywordCandidates[query]?.let(::listOf).orEmpty())
            },
            addressSearch = KakaoAddressSearchPort { query, _ ->
                ProviderSearchResult.Available(addressCandidates[query]?.let(::listOf).orEmpty())
            },
            targetWriter = targetWriter,
        )
        return ReviewCreateService(validator, targetResolver, reviews, transactionManager, CLOCK)
    }

    private fun createCommand(authorId: Long, target: RestaurantTargetCommand) = CreateReviewCommand(
        authorUserId = authorId,
        restaurantTarget = target,
        visitMonth = VISIT_MONTH,
        ratings = ratings(),
        comment = null,
    )

    private fun userFixture(): User = users.saveUser(User()).also { authorIds += it.id }

    private fun restaurantFixture(label: String): RestaurantFixture {
        val location = pickupLocations.save(
            PickupLocation(
                standardAddress = uniqueAddress(label),
                detailAddress = null,
                latitude = BigDecimal("37.50000000"),
                longitude = BigDecimal("127.00000000"),
                source = PickupLocationSource.MANUAL_ADDRESS,
            ),
        ).also { pickupLocationIds += it.id }
        val restaurant = restaurants.save(Restaurant("$label 브랜드", location)).also { restaurantIds += it.id }
        return RestaurantFixture(location, restaurant)
    }

    private fun trackRestaurant(restaurantId: Long) {
        val restaurant = requireNotNull(restaurants.findById(restaurantId))
        restaurantIds += restaurant.id
        pickupLocationIds += restaurant.pickupLocation.id
    }

    private fun backdate(reviewId: Long, days: Long) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            jdbcTemplate.update("update reviews set created_at = ? where id = ?", NOW.minusSeconds(days * 86_400), reviewId)
        }
    }

    private fun race(action: () -> ReviewResult): List<Result<ReviewResult>> {
        val ready = CountDownLatch(CONCURRENT_REQUESTS)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        return try {
            val futures = (0 until CONCURRENT_REQUESTS).map {
                executor.submit<Result<ReviewResult>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "Concurrent test start timed out" }
                    runCatching(action)
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
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(1)
        assertThat(results.single { it.isFailure }.exceptionOrNull())
            .isInstanceOf(StateConflictException::class.java)
    }

    private fun ratings() = ReviewRatings(
        ReviewRating.GOOD,
        ReviewRating.VERY_GOOD,
        ReviewRating.GOOD,
        ReviewRating.GOOD,
        ReviewRating.NOT_OBSERVED,
        ReviewRating.GOOD,
    )

    private fun changedRatings() = ReviewRatings(
        ReviewRating.VERY_GOOD,
        ReviewRating.GOOD,
        ReviewRating.NEEDS_IMPROVEMENT,
        ReviewRating.GOOD,
        ReviewRating.GOOD,
        ReviewRating.VERY_GOOD,
    )

    private fun uniqueAddress(label: String) = "서울 강남구 $label-${UUID.randomUUID()}"

    private data class RestaurantFixture(val location: PickupLocation, val restaurant: Restaurant)

    private companion object {
        const val CONCURRENT_REQUESTS = 2
        val NOW: Instant = Instant.parse("2026-07-26T03:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val VISIT_MONTH: VisitMonth = VisitMonth.parse("2026-07")
    }
}
