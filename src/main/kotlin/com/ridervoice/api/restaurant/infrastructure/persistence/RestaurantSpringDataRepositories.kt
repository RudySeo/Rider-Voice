package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail
import com.ridervoice.api.restaurant.application.model.StoredLinkedRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
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
        select new com.ridervoice.api.restaurant.application.model.StoredLinkedRestaurantSearchCandidate(
            restaurant.id,
            restaurant.kakaoPlaceId,
            restaurant.brandName,
            pickupLocation.standardAddress,
            restaurant.status
        )
        from Restaurant restaurant
        join restaurant.pickupLocation pickupLocation
        where restaurant.kakaoPlaceId in :kakaoPlaceIds
        """,
    )
    fun findSearchCandidatesByKakaoPlaceIds(
        @Param("kakaoPlaceIds") kakaoPlaceIds: Set<String>,
    ): List<StoredLinkedRestaurantSearchCandidate>

    @Query(
        """
        select new com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate(
            restaurant.id,
            restaurant.kakaoPlaceId,
            restaurant.brandName,
            pickupLocation.standardAddress
        )
        from Restaurant restaurant
        join restaurant.pickupLocation pickupLocation
        where restaurant.status = :status
          and (
              restaurant.brandName like concat('%', :normalizedQuery, '%')
              or pickupLocation.normalizedAddress like concat('%', :normalizedQuery, '%')
          )
        order by restaurant.id desc
        """,
    )
    fun searchActive(
        @Param("normalizedQuery") normalizedQuery: String,
        @Param("status") status: RestaurantStatus,
        pageable: Pageable,
    ): List<StoredRestaurantSearchCandidate>

    @Query(
        """
        select new com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate(
            restaurant.id,
            restaurant.kakaoPlaceId,
            restaurant.brandName,
            pickupLocation.standardAddress
        )
        from Restaurant restaurant
        join restaurant.pickupLocation pickupLocation
        where restaurant.id = :restaurantId
          and restaurant.status = :activeStatus
        """,
    )
    fun findSearchCandidateById(
        @Param("restaurantId") restaurantId: Long,
        @Param("activeStatus") activeStatus: RestaurantStatus = RestaurantStatus.ACTIVE,
    ): Optional<StoredRestaurantSearchCandidate>

    @Query(
        """
        select new com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail(
            restaurant.id,
            restaurant.brandName,
            pickupLocation.id,
            pickupLocation.standardAddress,
            pickupLocation.detailAddress,
            pickupLocation.latitude,
            pickupLocation.longitude,
            restaurant.status
        )
        from Restaurant restaurant
        join restaurant.pickupLocation pickupLocation
        where restaurant.id = :restaurantId
          and restaurant.status in :readableStatuses
        """,
    )
    fun findDetailById(
        @Param("restaurantId") restaurantId: Long,
        @Param("readableStatuses") readableStatuses: Set<RestaurantStatus>,
    ): Optional<StoredRestaurantDetail>

    fun findByIdAndStatus(restaurantId: Long, status: RestaurantStatus): Optional<Restaurant>

    fun findByKakaoPlaceId(kakaoPlaceId: String): Optional<Restaurant>

    fun findByPickupLocationIdAndBrandName(
        pickupLocationId: Long,
        brandName: String,
    ): Optional<Restaurant>
}

internal interface SpringDataRestaurantPlatformRepository : JpaRepository<RestaurantPlatform, Long> {
    fun findAllByRestaurantId(restaurantId: Long): List<RestaurantPlatform>
}
