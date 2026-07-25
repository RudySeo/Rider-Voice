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
import com.ridervoice.api.review.application.port.`in`.CreateReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewCommand
import com.ridervoice.api.review.application.port.`in`.DeleteReviewUseCase
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsCommand
import com.ridervoice.api.review.application.port.`in`.ListMyReviewsUseCase
import com.ridervoice.api.review.application.port.`in`.UpdateReviewCommand
import com.ridervoice.api.review.application.port.`in`.UpdateReviewUseCase
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateRepository
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateSnapshot
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.ReviewHistoryStatus
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Tag("integration")
class ReviewCoreIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var users: UserStore
    @Autowired private lateinit var pickupLocations: PickupLocationRepository
    @Autowired private lateinit var restaurants: RestaurantRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var states: AuthorRestaurantReviewStateRepository
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
            authorIds.forEach { authorId ->
                jdbcTemplate.update(
                    "delete from author_restaurant_review_states where author_user_id = ?",
                    authorId,
                )
                jdbcTemplate.update("delete from reviews where author_user_id = ?", authorId)
            }
            restaurantIds.forEach { restaurantId ->
                jdbcTemplate.update("delete from restaurant_platforms where restaurant_id = ?", restaurantId)
                jdbcTemplate.update(
                    "delete from restaurant_external_references where restaurant_id = ?",
                    restaurantId,
                )
                jdbcTemplate.update("delete from restaurants where id = ?", restaurantId)
            }
            pickupLocationIds.forEach { pickupLocationId ->
                jdbcTemplate.update("delete from pickup_locations where id = ?", pickupLocationId)
            }
            authorIds.forEach { authorId ->
                jdbcTemplate.update("delete from users where id = ?", authorId)
            }
        }
        authorIds.clear()
        restaurantIds.clear()
        pickupLocationIds.clear()
    }

    @Test
    fun `existing Kakao and both manual targets create one current review each`() {
        val fixture = restaurantFixture("target")
        val author = userFixture()
        val kakaoCandidate = ExternalRestaurantCandidate(
            externalPlaceId = "review-kakao-${UUID.randomUUID()}",
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
            KakaoRestaurantTargetCommand("카카오 검색", kakaoCandidate.externalPlaceId),
            ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = fixture.location.id,
                name = "기존 장소 새 브랜드",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
            ManualAddressRestaurantTargetCommand(
                addressQuery = "수동 주소 검색",
                selectedStandardAddress = manualAddressCandidate.standardAddress,
                detailAddress = "지하 픽업대",
                name = "수동 주소 새 브랜드",
                platforms = setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )

        val results = targets.map { target ->
            service.create(createCommand(author.id, target)).also { trackRestaurant(it.restaurant.restaurantId) }
        }

        assertThat(results.map { it.sequence }).containsOnly(1L)
        assertThat(results.map { it.historyStatus }).containsOnly(ReviewHistoryStatus.CURRENT)
        assertThat(results.map { it.restaurant.restaurantId }).doesNotHaveDuplicates()
        assertThat(
            states.findByAuthorUserIdAndRestaurantIds(
                author.id,
                results.mapTo(linkedSetOf()) { it.restaurant.restaurantId },
            ).map { it.currentReviewId },
        ).containsExactlyInAnyOrderElementsOf(results.map { it.reviewId })
    }

    @Test
    fun `concurrent first submissions leave one state and one winning review`() {
        val fixture = restaurantFixture("first-state-race")
        val author = userFixture()
        val synchronizedStates = FirstMissingStateBarrierRepository(states)
        val service = createService(stateRepository = synchronizedStates)

        val results = race {
            service.create(createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id)))
        }

        assertOneSuccessAndOneConflict(results)
        val state = loadState(author.id, fixture.restaurant.id)
        assertThat(state.lastSequence).isEqualTo(1L)
        assertThat(state.currentReviewId).isEqualTo(results.single { it.isSuccess }.getOrThrow().reviewId)
        assertThat(reviews.findByAuthorUserId(author.id, null, 10)).hasSize(1)
    }

    @Test
    fun `eligible concurrent submissions serialize sequence and reapply the ninety day lock`() {
        val fixture = restaurantFixture("existing-state-race")
        val author = userFixture()
        val service = createService()
        val command = createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id))
        val first = service.create(command)
        backdateState(author.id, fixture.restaurant.id, first.reviewId)

        val results = race { service.create(command) }

        assertOneSuccessAndOneConflict(results)
        val winner = results.single { it.isSuccess }.getOrThrow()
        val state = loadState(author.id, fixture.restaurant.id)
        assertThat(winner.sequence).isEqualTo(2L)
        assertThat(state.lastSequence).isEqualTo(2L)
        assertThat(state.currentReviewId).isEqualTo(winner.reviewId)
        assertThat(reviews.findByAuthorUserId(author.id, null, 10).map { it.sequence })
            .containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `only latest review updates and deletes while history and cooldown state remain`() {
        val fixture = restaurantFixture("owner-lifecycle")
        val author = userFixture()
        val service = createService()
        val command = createCommand(author.id, ExistingRestaurantTargetCommand(fixture.restaurant.id))
        val first = service.create(command)
        backdateState(author.id, fixture.restaurant.id, first.reviewId)
        val second = service.create(command)

        assertThatThrownBy {
            updateReview.update(UpdateReviewCommand(author.id, first.reviewId, changedRatings(), null))
        }.isInstanceOf(ResourceNotFoundException::class.java)

        val updated = updateReview.update(
            UpdateReviewCommand(author.id, second.reviewId, changedRatings(), "  다시 검수  "),
        )
        assertThat(updated.visitMonth).isEqualTo(VISIT_MONTH)
        assertThat(updated.ratings).isEqualTo(changedRatings())
        assertThat(updated.comment).isEqualTo("다시 검수")

        val beforeDelete = listMyReviews.list(ListMyReviewsCommand(author.id, null, 10))
        assertThat(beforeDelete.items.associate { it.reviewId to it.historyStatus }).containsAllEntriesOf(
            mapOf(
                first.reviewId to ReviewHistoryStatus.HISTORY,
                second.reviewId to ReviewHistoryStatus.CURRENT,
            ),
        )

        deleteReview.delete(DeleteReviewCommand(author.id, second.reviewId))

        val retainedState = loadState(author.id, fixture.restaurant.id)
        assertThat(retainedState.lastSubmittedAt).isEqualTo(NOW)
        assertThat(retainedState.lastSequence).isEqualTo(2L)
        assertThat(retainedState.currentReviewId).isNull()
        val afterDelete = listMyReviews.list(ListMyReviewsCommand(author.id, null, 10))
        assertThat(afterDelete.items.map { it.reviewId }).containsExactly(first.reviewId)
        assertThat(afterDelete.items.single().historyStatus).isEqualTo(ReviewHistoryStatus.HISTORY)
        assertThatThrownBy { service.create(command) }
            .isInstanceOf(StateConflictException::class.java)
            .hasMessageContaining("90 days")
    }

    private fun createService(
        keywordCandidates: Map<String, ExternalRestaurantCandidate> = emptyMap(),
        addressCandidates: Map<String, ExternalAddressCandidate> = emptyMap(),
        stateRepository: AuthorRestaurantReviewStateRepository = states,
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
        return ReviewCreateService(
            targetValidator = validator,
            targetResolver = targetResolver,
            reviews = reviews,
            states = stateRepository,
            transactionManager = transactionManager,
            clock = CLOCK,
        )
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
        val restaurant = restaurants.save(Restaurant("$label 브랜드", location)).also {
            restaurantIds += it.id
        }
        return RestaurantFixture(location, restaurant)
    }

    private fun trackRestaurant(restaurantId: Long) {
        val restaurant = requireNotNull(restaurants.findById(restaurantId))
        restaurantIds += restaurant.id
        pickupLocationIds += restaurant.pickupLocation.id
    }

    private fun backdateState(authorId: Long, restaurantId: Long, currentReviewId: Long) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            val state = requireNotNull(states.findForUpdate(authorId, restaurantId))
            states.save(
                state.copy(
                    lastSubmittedAt = NOW.minus(Duration.ofDays(91)),
                    currentReviewId = currentReviewId,
                ),
            )
        }
    }

    private fun loadState(authorId: Long, restaurantId: Long): AuthorRestaurantReviewStateSnapshot =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                states.findForUpdate(authorId, restaurantId)
            },
        )

    private fun race(action: () -> com.ridervoice.api.review.application.model.ReviewResult):
        List<Result<com.ridervoice.api.review.application.model.ReviewResult>> {
        val ready = CountDownLatch(CONCURRENT_REQUESTS)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        return try {
            val futures = (0 until CONCURRENT_REQUESTS).map {
                executor.submit<Result<com.ridervoice.api.review.application.model.ReviewResult>> {
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
        pickupSpaceCleanliness = ReviewRating.GOOD,
        packagingStability = ReviewRating.VERY_GOOD,
        orderReadiness = ReviewRating.GOOD,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.NOT_OBSERVED,
        riderRespect = ReviewRating.GOOD,
    )

    private fun changedRatings() = ReviewRatings(
        pickupSpaceCleanliness = ReviewRating.VERY_GOOD,
        packagingStability = ReviewRating.GOOD,
        orderReadiness = ReviewRating.NEEDS_IMPROVEMENT,
        handoffAccuracy = ReviewRating.GOOD,
        staffInteraction = ReviewRating.GOOD,
        riderRespect = ReviewRating.VERY_GOOD,
    )

    private fun uniqueAddress(label: String) = "서울 강남구 $label-${UUID.randomUUID()}"

    private data class RestaurantFixture(
        val location: PickupLocation,
        val restaurant: Restaurant,
    )

    private class FirstMissingStateBarrierRepository(
        private val delegate: AuthorRestaurantReviewStateRepository,
    ) : AuthorRestaurantReviewStateRepository by delegate {
        private val missingLookups = AtomicInteger()
        private val bothMissing = CountDownLatch(CONCURRENT_REQUESTS)

        override fun findForUpdate(
            authorUserId: Long,
            restaurantId: Long,
        ): AuthorRestaurantReviewStateSnapshot? = delegate.findForUpdate(authorUserId, restaurantId).also { state ->
            if (state == null && missingLookups.incrementAndGet() <= CONCURRENT_REQUESTS) {
                bothMissing.countDown()
                check(bothMissing.await(10, TimeUnit.SECONDS)) {
                    "Concurrent first state lookups did not meet"
                }
            }
        }
    }

    private companion object {
        const val CONCURRENT_REQUESTS = 2
        val NOW: Instant = Instant.parse("2026-07-26T03:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val VISIT_MONTH: VisitMonth = VisitMonth.parse("2026-07")
    }
}
