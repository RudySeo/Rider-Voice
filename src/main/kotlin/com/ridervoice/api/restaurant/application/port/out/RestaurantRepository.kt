package com.ridervoice.api.restaurant.application.port.out

import com.ridervoice.api.restaurant.domain.Restaurant

interface RestaurantRepository {
    fun searchByNameOrAddress(query: String): List<Restaurant>

    fun findById(id: Long): Restaurant?

    fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant?

    fun save(restaurant: Restaurant): Restaurant
}
