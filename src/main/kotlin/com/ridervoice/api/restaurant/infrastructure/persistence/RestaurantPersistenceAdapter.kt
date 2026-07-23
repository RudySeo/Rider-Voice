package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.Restaurant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class RestaurantPersistenceAdapter(
    private val springDataRepository: SpringDataRestaurantRepository,
) : RestaurantRepository {

    override fun searchByNameOrAddress(query: String): List<Restaurant> =
        springDataRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(query, query)

    override fun findById(id: UUID): Restaurant? = springDataRepository.findById(id).orElse(null)

    override fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant? =
        springDataRepository.findByKakaoPlaceId(kakaoPlaceId)

    override fun save(restaurant: Restaurant): Restaurant = springDataRepository.saveAndFlush(restaurant)
}

internal interface SpringDataRestaurantRepository : JpaRepository<Restaurant, UUID> {
    fun findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
        name: String,
        address: String,
    ): List<Restaurant>

    fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant?
}
