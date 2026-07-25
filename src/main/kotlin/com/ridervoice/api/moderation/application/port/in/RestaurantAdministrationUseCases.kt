package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.RestaurantMergeResult
import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult

fun interface MergeRestaurantUseCase {
    fun merge(command: MergeRestaurantCommand): RestaurantMergeResult
}

data class MergeRestaurantCommand(
    val adminUserId: Long,
    val duplicateRestaurantId: Long,
    val canonicalRestaurantId: Long,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(duplicateRestaurantId > 0) { "Duplicate restaurant ID must be positive" }
        require(canonicalRestaurantId > 0) { "Canonical restaurant ID must be positive" }
        require(duplicateRestaurantId != canonicalRestaurantId) {
            "A restaurant cannot be merged into itself"
        }
    }
}

fun interface RelinkRestaurantPickupLocationUseCase {
    fun relinkPickupLocation(
        command: RelinkRestaurantPickupLocationCommand,
    ): RestaurantPickupRelinkResult
}

data class RelinkRestaurantPickupLocationCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val pickupLocationId: Long,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
    }
}
