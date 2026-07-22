package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.domain.Restaurant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface RestaurantRepository : JpaRepository<Restaurant, UUID> {
    fun findByKakaoPlaceId(kakaoPlaceId: String): Optional<Restaurant>
}
