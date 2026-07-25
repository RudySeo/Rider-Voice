package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

internal interface SpringDataPickupLocationRepository : JpaRepository<PickupLocation, Long> {
    fun findByLocationKey(locationKey: String): Optional<PickupLocation>
}

internal interface SpringDataRestaurantRepository : JpaRepository<Restaurant, Long> {

    @Query(
        """
        select new com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate(
            restaurant.id,
            externalReference.externalPlaceId,
            restaurant.brandName,
            pickupLocation.standardAddress
        )
        from Restaurant restaurant
        join restaurant.pickupLocation pickupLocation
        left join RestaurantExternalReference externalReference
            on externalReference.restaurant = restaurant
            and externalReference.provider = :externalProvider
        where restaurant.status = :status
          and (
              restaurant.normalizedName like concat('%', :normalizedQuery, '%')
              or pickupLocation.normalizedAddress like concat('%', :normalizedQuery, '%')
          )
        order by restaurant.id desc
        """,
    )
    fun searchActive(
        @Param("normalizedQuery") normalizedQuery: String,
        @Param("status") status: RestaurantStatus,
        @Param("externalProvider") externalProvider: RestaurantExternalProvider,
        pageable: Pageable,
    ): List<StoredRestaurantSearchCandidate>

    @Query(
        """
        select case
            when restaurant.status = :activeStatus then restaurant.id
            else canonicalRestaurant.id
        end
        from Restaurant restaurant
        left join restaurant.canonicalRestaurant canonicalRestaurant
        where restaurant.id = :restaurantId
        """,
    )
    fun findCanonicalTargetIdById(
        @Param("restaurantId") restaurantId: Long,
        @Param("activeStatus") activeStatus: RestaurantStatus,
    ): Optional<Long>

    fun findByPickupLocationIdAndNormalizedName(
        pickupLocationId: Long,
        normalizedName: String,
    ): Optional<Restaurant>
}

internal interface SpringDataRestaurantExternalReferenceRepository :
    JpaRepository<RestaurantExternalReference, Long> {

    fun findByProviderAndExternalPlaceId(
        provider: RestaurantExternalProvider,
        externalPlaceId: String,
    ): Optional<RestaurantExternalReference>
}

internal interface SpringDataRestaurantPlatformRepository : JpaRepository<RestaurantPlatform, Long> {
    fun findAllByRestaurantId(restaurantId: Long): List<RestaurantPlatform>
}
