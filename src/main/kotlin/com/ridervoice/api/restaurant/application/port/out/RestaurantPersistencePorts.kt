package com.ridervoice.api.restaurant.application.port.out

import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportResult
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportResult
import com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform

interface PickupLocationRepository {
    fun findById(pickupLocationId: Long): PickupLocation?
    fun findByLocationKey(locationKey: String): PickupLocation?
    fun save(pickupLocation: PickupLocation): PickupLocation
}

interface RestaurantRepository {
    fun searchActive(query: String, limit: Int): List<StoredRestaurantSearchCandidate>
    fun findSearchCandidateById(restaurantId: Long): StoredRestaurantSearchCandidate?
    fun findById(restaurantId: Long): Restaurant?
    fun findCanonicalById(restaurantId: Long): Restaurant?
    fun findByPickupLocationIdAndBrandName(
        pickupLocationId: Long,
        brandName: String,
    ): Restaurant?

    fun save(restaurant: Restaurant): Restaurant
}

fun interface RestaurantDetailQuery {
    fun findCanonicalDetail(restaurantId: Long): StoredRestaurantDetail?
}

interface RestaurantReportProvider {
    fun getBrandReport(restaurantId: Long): RestaurantBrandReportResult

    fun getPickupLocationReport(pickupLocationId: Long): RestaurantPickupLocationReportResult
}

interface RestaurantExternalReferenceRepository {
    fun findByProviderAndExternalPlaceId(
        provider: RestaurantExternalProvider,
        externalPlaceId: String,
    ): RestaurantExternalReference?

    fun save(reference: RestaurantExternalReference): RestaurantExternalReference
}

interface RestaurantPlatformRepository {
    fun findPlatforms(restaurantId: Long): Set<DeliveryPlatform>
    fun saveAll(platforms: Collection<RestaurantPlatform>): List<RestaurantPlatform>
}
