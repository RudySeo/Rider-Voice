package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
@Tag("integration")
class RestaurantPersistenceIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var pickupLocations: PickupLocationRepository

    @Autowired
    private lateinit var restaurants: RestaurantRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `adapter persists exact identities and searches active restaurants`() {
        val location = pickupLocations.save(pickupLocation())
        val restaurant = restaurants.save(Restaurant("Rider Voice 대표 브랜드", location, "integration-place"))
        entityManager.clear()

        assertThat(pickupLocations.findByLocationKey(location.locationKey)?.id).isEqualTo(location.id)
        assertThat(
            restaurants.findByPickupLocationIdAndBrandName(location.id, restaurant.brandName.lowercase())?.id,
        ).isEqualTo(restaurant.id)
        assertThat(restaurants.findActiveById(restaurant.id)?.id).isEqualTo(restaurant.id)
        assertThat(restaurants.findByKakaoPlaceId("integration-place")?.id).isEqualTo(restaurant.id)
        assertThat(restaurants.searchActive("rider voice", 20).map { it.restaurantId })
            .contains(restaurant.id)
    }

    @Test
    fun `location key uniqueness is enforced when a concurrent loser flushes`() {
        pickupLocations.save(pickupLocation())

        assertThatThrownBy { pickupLocations.save(pickupLocation()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `brand name is unique within a pickup location regardless of unicode whitespace and case`() {
        val location = pickupLocations.save(pickupLocation())
        restaurants.save(Restaurant("Ｒｉｄｅｒ Voice 대표 브랜드", location))

        assertThatThrownBy { restaurants.save(Restaurant("  rider　voice 대표 브랜드  ", location)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `Kakao place identity is globally unique`() {
        val location = pickupLocations.save(pickupLocation())
        restaurants.save(Restaurant("첫 브랜드", location, "same-place"))

        assertThatThrownBy {
            restaurants.save(Restaurant("두 번째 브랜드", location, "same-place"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun pickupLocation() = PickupLocation(
        standardAddress = "서울 강남구 테헤란로 1",
        detailAddress = "지하 1층",
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
        source = PickupLocationSource.KAKAO,
    )
}
