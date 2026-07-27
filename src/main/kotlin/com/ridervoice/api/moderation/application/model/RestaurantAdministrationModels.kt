package com.ridervoice.api.moderation.application.model

import com.ridervoice.api.restaurant.domain.RestaurantStatus
import java.time.Instant

data class RestaurantMergeResult(
    val restaurantId: Long,
    val status: RestaurantStatus,
    val canonicalRestaurantId: Long,
    val completedAt: Instant,
)

data class RestaurantPickupRelinkResult(
    val restaurantId: Long,
    val pickupLocationId: Long,
    val completedAt: Instant,
)

data class RestaurantRenameResult(
    val restaurantId: Long,
    val name: String,
    val completedAt: Instant,
)

data class RestaurantStatusChangeResult(
    val restaurantId: Long,
    val status: RestaurantStatus,
    val completedAt: Instant,
)
