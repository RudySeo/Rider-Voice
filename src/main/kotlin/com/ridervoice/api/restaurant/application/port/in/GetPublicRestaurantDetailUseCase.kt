package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult

fun interface GetPublicRestaurantDetailUseCase {
    fun get(restaurantId: Long): PublicRestaurantDetailResult
}

fun interface ResolveReadableRestaurantUseCase {
    fun resolve(restaurantId: Long): Long
}
