package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.port.out.RestaurantDetailQuery
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantPlatformRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
internal class PickupLocationPersistenceAdapter(
    private val pickupLocations: SpringDataPickupLocationRepository,
) : PickupLocationRepository {

    override fun findById(pickupLocationId: Long): PickupLocation? =
        pickupLocations.findById(pickupLocationId).orElse(null)

    override fun findByLocationKey(locationKey: String): PickupLocation? =
        pickupLocations.findByLocationKey(locationKey).orElse(null)

    override fun save(pickupLocation: PickupLocation): PickupLocation =
        pickupLocations.saveAndFlush(pickupLocation)
}

@Component
internal class RestaurantPersistenceAdapter(
    private val restaurants: SpringDataRestaurantRepository,
) : RestaurantRepository, RestaurantDetailQuery {

    override fun searchActive(query: String, limit: Int): List<StoredRestaurantSearchCandidate> =
        restaurants.searchActive(
            normalizedQuery = RestaurantNormalization.normalizedText(query),
            status = RestaurantStatus.ACTIVE,
            pageable = PageRequest.of(0, limit),
        )

    override fun findSearchCandidateById(restaurantId: Long): StoredRestaurantSearchCandidate? =
        restaurants.findSearchCandidateById(restaurantId, RestaurantStatus.ACTIVE).orElse(null)

    override fun findById(restaurantId: Long): Restaurant? =
        restaurants.findById(restaurantId).orElse(null)

    override fun findActiveById(restaurantId: Long): Restaurant? =
        restaurants.findByIdAndStatus(restaurantId, RestaurantStatus.ACTIVE).orElse(null)

    override fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant? =
        restaurants.findByKakaoPlaceId(kakaoPlaceId.trim()).orElse(null)

    override fun findDetail(restaurantId: Long): StoredRestaurantDetail? =
        restaurants.findDetailById(
            restaurantId,
            setOf(RestaurantStatus.ACTIVE, RestaurantStatus.CLOSED),
        ).orElse(null)

    override fun findByPickupLocationIdAndBrandName(
        pickupLocationId: Long,
        brandName: String,
    ): Restaurant? = restaurants.findByPickupLocationIdAndBrandName(
        pickupLocationId,
        RestaurantNormalization.displayText(brandName),
    ).orElse(null)

    override fun save(restaurant: Restaurant): Restaurant = restaurants.saveAndFlush(restaurant)
}

@Component
internal class RestaurantPlatformPersistenceAdapter(
    private val platforms: SpringDataRestaurantPlatformRepository,
) : RestaurantPlatformRepository {

    override fun findPlatforms(restaurantId: Long): Set<DeliveryPlatform> =
        platforms.findAllByRestaurantId(restaurantId).mapTo(linkedSetOf(), RestaurantPlatform::platform)

    override fun saveAll(platforms: Collection<RestaurantPlatform>): List<RestaurantPlatform> =
        this.platforms.saveAllAndFlush(platforms)
}
