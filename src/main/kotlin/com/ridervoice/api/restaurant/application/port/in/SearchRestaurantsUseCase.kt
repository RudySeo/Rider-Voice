package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult

fun interface SearchRestaurantsUseCase {
    fun search(command: SearchRestaurantsCommand): RestaurantSearchResult
}

data class SearchRestaurantsCommand(
    val query: String,
) {
    init {
        require(query.isNotBlank()) { "Search query must not be blank" }
    }
}
