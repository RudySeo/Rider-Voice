package com.ridervoice.api.moderation.application.port.out

import com.ridervoice.api.restaurant.domain.RestaurantStatus

interface RestaurantAdministrationRepository {
    fun findRestaurantsForUpdate(restaurantIds: Set<Long>): List<StoredAdminRestaurant>

    fun pickupLocationExists(pickupLocationId: Long): Boolean

    fun restaurantNameExistsAtPickupLocation(
        pickupLocationId: Long,
        brandName: String,
        excludedRestaurantId: Long,
    ): Boolean

    fun relinkPickupLocation(command: RestaurantPickupRelinkPersistenceCommand): StoredAdminRestaurant
    fun rename(command: RestaurantRenamePersistenceCommand): StoredAdminRestaurant
    fun changeStatus(command: RestaurantStatusPersistenceCommand): StoredAdminRestaurant
    fun findOrCreateVerifiedPickupLocation(command: VerifiedPickupLocationPersistenceCommand): Long
}

data class StoredAdminRestaurant(
    val restaurantId: Long,
    val brandName: String,
    val pickupLocationId: Long,
    val status: RestaurantStatus,
)

data class RestaurantPickupRelinkPersistenceCommand(
    val restaurantId: Long,
    val pickupLocationId: Long,
)

data class RestaurantRenamePersistenceCommand(
    val restaurantId: Long,
    val name: String,
)

data class RestaurantStatusPersistenceCommand(
    val restaurantId: Long,
    val status: RestaurantStatus,
)

data class VerifiedPickupLocationPersistenceCommand(
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: java.math.BigDecimal,
    val longitude: java.math.BigDecimal,
)
