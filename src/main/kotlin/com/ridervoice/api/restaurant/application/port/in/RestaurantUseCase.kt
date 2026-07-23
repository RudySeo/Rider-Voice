package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.RegisterRestaurantCommand
import com.ridervoice.api.restaurant.application.model.RestaurantRegistrationResult
import com.ridervoice.api.restaurant.application.model.RestaurantSearchQuery
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult

interface RestaurantUseCase {
    fun search(query: RestaurantSearchQuery): RestaurantSearchResult

    fun register(command: RegisterRestaurantCommand): RestaurantRegistrationResult
}
