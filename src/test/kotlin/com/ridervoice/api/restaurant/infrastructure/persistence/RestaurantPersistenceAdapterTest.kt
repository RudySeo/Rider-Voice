package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.util.Optional

class RestaurantPersistenceAdapterTest {

    @Test
    fun `pickup location operations use exact location key lookup and flushing save`() {
        val location = pickupLocation().also { it.id = 10L }
        val calls = mutableListOf<String>()
        val pickups = fakeRepository(SpringDataPickupLocationRepository::class.java) { method, arguments ->
            calls += method.name
            when (method.name) {
                "findById" -> Optional.of(location)
                "findByLocationKey" -> {
                    assertThat(arguments.single()).isEqualTo(location.locationKey)
                    Optional.of(location)
                }
                "saveAndFlush" -> location
                else -> unexpected(method)
            }
        }
        val adapter = PickupLocationPersistenceAdapter(pickups)

        assertThat(adapter.findById(location.id)).isSameAs(location)
        assertThat(adapter.findByLocationKey(location.locationKey)).isSameAs(location)
        assertThat(adapter.save(location)).isSameAs(location)
        assertThat(calls).containsExactly("findById", "findByLocationKey", "saveAndFlush")
    }

    @Test
    fun `restaurant operations normalize search and follow the canonical chain`() {
        val location = pickupLocation().also { it.id = 20L }
        val restaurant = Restaurant("대표 브랜드", location).also { it.id = 3L }
        val candidate = StoredRestaurantSearchCandidate(
            restaurantId = restaurant.id,
            externalPlaceId = "kakao-3",
            name = restaurant.brandName,
            address = location.standardAddress,
        )
        val detail = StoredRestaurantDetail(
            restaurantId = restaurant.id,
            name = restaurant.brandName,
            pickupLocationId = location.id,
            standardAddress = location.standardAddress,
            detailAddress = location.detailAddress,
            latitude = location.latitude,
            longitude = location.longitude,
        )
        val calls = mutableListOf<String>()
        val restaurants = fakeRepository(SpringDataRestaurantRepository::class.java) { method, arguments ->
            calls += method.name
            when (method.name) {
                "searchActive" -> {
                    assertThat(arguments[0]).isEqualTo("good food")
                    assertThat(arguments[1]).isEqualTo(RestaurantStatus.ACTIVE)
                    assertThat(arguments[2]).isEqualTo(RestaurantExternalProvider.KAKAO)
                    assertThat(arguments[3]).isInstanceOf(Pageable::class.java)
                    assertThat((arguments[3] as Pageable).pageSize).isEqualTo(7)
                    listOf(candidate)
                }
                "findCanonicalTargetIdById" -> {
                    assertThat(arguments[1]).isEqualTo(RestaurantStatus.ACTIVE)
                    Optional.of(
                        when (arguments[0]) {
                            1L -> 2L
                            2L -> 3L
                            else -> 3L
                        },
                    )
                }
                "findReadableCanonicalTargetIdById" -> {
                    assertThat(arguments[1]).isEqualTo(RestaurantStatus.MERGED)
                    Optional.of(
                        when (arguments[0]) {
                            1L -> 2L
                            2L -> 3L
                            else -> 3L
                        },
                    )
                }
                "findById" -> Optional.of(restaurant)
                "findDetailById" -> {
                    assertThat(arguments[0]).isEqualTo(restaurant.id)
                    assertThat(arguments[1]).isEqualTo(setOf(RestaurantStatus.ACTIVE, RestaurantStatus.CLOSED))
                    Optional.of(detail)
                }
                "findByPickupLocationIdAndBrandName" -> Optional.of(restaurant)
                "saveAndFlush" -> restaurant
                else -> unexpected(method)
            }
        }
        val adapter = RestaurantPersistenceAdapter(restaurants)

        assertThat(adapter.searchActive("  ＧＯＯＤ　Food  ", 7)).containsExactly(candidate)
        assertThat(adapter.findCanonicalById(1L)).isSameAs(restaurant)
        assertThat(adapter.findCanonicalDetail(1L)).isEqualTo(detail)
        assertThat(adapter.findById(restaurant.id)).isSameAs(restaurant)
        assertThat(
            adapter.findByPickupLocationIdAndBrandName(location.id, restaurant.brandName),
        ).isSameAs(restaurant)
        assertThat(adapter.save(restaurant)).isSameAs(restaurant)
        assertThat(calls).containsExactly(
            "searchActive",
            "findCanonicalTargetIdById",
            "findCanonicalTargetIdById",
            "findCanonicalTargetIdById",
            "findById",
            "findReadableCanonicalTargetIdById",
            "findReadableCanonicalTargetIdById",
            "findReadableCanonicalTargetIdById",
            "findDetailById",
            "findById",
            "findByPickupLocationIdAndBrandName",
            "saveAndFlush",
        )
    }

    @Test
    fun `missing or cyclic canonical chains do not return a restaurant`() {
        val missing = fakeRepository(SpringDataRestaurantRepository::class.java) { method, _ ->
            when (method.name) {
                "findCanonicalTargetIdById" -> Optional.empty<Long>()
                else -> unexpected(method)
            }
        }
        val cyclic = fakeRepository(SpringDataRestaurantRepository::class.java) { method, arguments ->
            when (method.name) {
                "findCanonicalTargetIdById" -> Optional.of(if (arguments[0] == 1L) 2L else 1L)
                else -> unexpected(method)
            }
        }

        assertThat(RestaurantPersistenceAdapter(missing).findCanonicalById(99L)).isNull()
        assertThat(RestaurantPersistenceAdapter(cyclic).findCanonicalById(1L)).isNull()
    }

    @Test
    fun `external reference and platform operations preserve exact repository contracts`() {
        val restaurant = Restaurant("브랜드", pickupLocation()).also { it.id = 30L }
        val reference = RestaurantExternalReference(
            restaurant,
            RestaurantExternalProvider.KAKAO,
            "place-30",
        )
        val platform = RestaurantPlatform(restaurant, DeliveryPlatform.BAEMIN)
        val references = fakeRepository(SpringDataRestaurantExternalReferenceRepository::class.java) {
                method, arguments ->
            when (method.name) {
                "findByProviderAndExternalPlaceId" -> {
                    assertThat(arguments).containsExactly(RestaurantExternalProvider.KAKAO, "place-30")
                    Optional.of(reference)
                }
                "saveAndFlush" -> reference
                else -> unexpected(method)
            }
        }
        val platforms = fakeRepository(SpringDataRestaurantPlatformRepository::class.java) { method, arguments ->
            when (method.name) {
                "findAllByRestaurantId" -> {
                    assertThat(arguments.single()).isEqualTo(restaurant.id)
                    listOf(platform)
                }
                "saveAllAndFlush" -> listOf(platform)
                else -> unexpected(method)
            }
        }
        val referenceAdapter = RestaurantExternalReferencePersistenceAdapter(references)
        val platformAdapter = RestaurantPlatformPersistenceAdapter(platforms)

        assertThat(
            referenceAdapter.findByProviderAndExternalPlaceId(RestaurantExternalProvider.KAKAO, "place-30"),
        ).isSameAs(reference)
        assertThat(referenceAdapter.save(reference)).isSameAs(reference)
        assertThat(platformAdapter.findPlatforms(restaurant.id)).containsExactly(DeliveryPlatform.BAEMIN)
        assertThat(platformAdapter.saveAll(listOf(platform))).containsExactly(platform)
    }

    private fun pickupLocation() = PickupLocation(
        standardAddress = "서울 강남구 테헤란로 1",
        detailAddress = null,
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        source = PickupLocationSource.KAKAO,
    )

    private fun <T> fakeRepository(
        type: Class<T>,
        handler: (Method, List<Any?>) -> Any?,
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Fake${type.simpleName}"
                else -> handler(method, arguments?.toList().orEmpty())
            }
        },
    )

    private fun unexpected(method: Method): Nothing =
        error("Unexpected repository method: ${method.name}")
}
