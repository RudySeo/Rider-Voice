package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Tag("integration")
class RestaurantUniqueConstraintConcurrencyIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var pickupLocations: SpringDataPickupLocationRepository

    @Autowired
    private lateinit var restaurants: SpringDataRestaurantRepository

    @Autowired
    private lateinit var externalReferences: SpringDataRestaurantExternalReferenceRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val pickupLocationIds = ConcurrentHashMap.newKeySet<Long>()
    private val restaurantIds = ConcurrentHashMap.newKeySet<Long>()
    private val externalReferenceIds = ConcurrentHashMap.newKeySet<Long>()

    @AfterEach
    fun removeCommittedFixtures() {
        externalReferences.deleteAllById(externalReferenceIds)
        externalReferences.flush()
        restaurants.deleteAllById(restaurantIds)
        restaurants.flush()
        pickupLocations.deleteAllById(pickupLocationIds)
        pickupLocations.flush()
        externalReferenceIds.clear()
        restaurantIds.clear()
        pickupLocationIds.clear()
    }

    @Test
    fun `concurrent location inserts are serialized by the location key unique constraint`() {
        val standardAddress = "서울 강남구 동시로 ${UUID.randomUUID()}"
        val locationKey = pickupLocation(standardAddress).locationKey

        val results = race { _ ->
            inTransaction {
                pickupLocations.saveAndFlush(pickupLocation(standardAddress)).also {
                    pickupLocationIds += it.id
                }
            }
        }

        assertSingleDatabaseWinner(results)
        assertThat(pickupLocations.findByLocationKey(locationKey)).isPresent
    }

    @Test
    fun `concurrent brand inserts are serialized by the location and normalized name unique constraint`() {
        val location = persistLocation("서울 강남구 브랜드로 ${UUID.randomUUID()}")

        val results = race { _ ->
            inTransaction {
                val attachedLocation = pickupLocations.findById(location.id).orElseThrow()
                restaurants.saveAndFlush(Restaurant("동시 등록 브랜드", attachedLocation)).also {
                    restaurantIds += it.id
                }
            }
        }

        assertSingleDatabaseWinner(results)
        assertThat(
            restaurants.findByPickupLocationIdAndBrandName(location.id, "동시 등록 브랜드"),
        ).isPresent
    }

    @Test
    fun `concurrent external reference inserts are serialized by provider and place identity`() {
        val location = persistLocation("서울 강남구 외부참조로 ${UUID.randomUUID()}")
        val candidates = listOf(
            persistRestaurant("외부 참조 첫 브랜드", location.id),
            persistRestaurant("외부 참조 둘째 브랜드", location.id),
        )
        val externalPlaceId = "concurrent-place-${UUID.randomUUID()}"

        val results = race { index ->
            inTransaction {
                val attachedRestaurant = restaurants.findById(candidates[index].id).orElseThrow()
                externalReferences.saveAndFlush(
                    RestaurantExternalReference(
                        restaurant = attachedRestaurant,
                        provider = RestaurantExternalProvider.KAKAO,
                        externalPlaceId = externalPlaceId,
                    ),
                ).also { externalReferenceIds += it.id }
            }
        }

        assertSingleDatabaseWinner(results)
        assertThat(
            externalReferences.findByProviderAndExternalPlaceId(
                RestaurantExternalProvider.KAKAO,
                externalPlaceId,
            ),
        ).isPresent
    }

    private fun persistLocation(standardAddress: String): PickupLocation =
        pickupLocations.saveAndFlush(pickupLocation(standardAddress)).also {
            pickupLocationIds += it.id
        }

    private fun persistRestaurant(name: String, pickupLocationId: Long): Restaurant =
        inTransaction {
            val location = pickupLocations.findById(pickupLocationId).orElseThrow()
            restaurants.saveAndFlush(Restaurant(name, location)).also {
                restaurantIds += it.id
            }
        }

    private fun pickupLocation(standardAddress: String) = PickupLocation(
        standardAddress = standardAddress,
        detailAddress = "동시성 테스트 픽업대",
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
        source = PickupLocationSource.MANUAL_ADDRESS,
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
            futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun <T> inTransaction(action: () -> T): T =
        TransactionTemplate(transactionManager).execute { action() }
            ?: error("Transaction returned no result")

    private fun assertSingleDatabaseWinner(results: List<Result<*>>) {
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(1)
        assertThat(results.single { it.isFailure }.exceptionOrNull())
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private companion object {
        const val CONCURRENT_REQUESTS = 2
    }
}
