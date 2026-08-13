package com.ridervoice.api.restaurant.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "restaurants",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_restaurants_pickup_location_brand_name",
            columnNames = ["pickup_location_id", "brand_name"],
        ),
        UniqueConstraint(
            name = "uk_restaurants_kakao_place_id",
            columnNames = ["kakao_place_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_restaurants_status_brand_name",
            columnList = "status, brand_name",
        ),
    ],
)
class Restaurant(
    brandName: String,
    pickupLocation: PickupLocation,
    kakaoPlaceId: String? = null,
) : BaseEntity() {

    @field:Column(name = "brand_name", nullable = false, length = 255)
    final var brandName: String = RestaurantNormalization.displayText(brandName)
        private set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "pickup_location_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_restaurants_pickup_location"),
    )
    final var pickupLocation: PickupLocation = pickupLocation
        private set

    @field:Column(name = "kakao_place_id", length = 255)
    final var kakaoPlaceId: String? = kakaoPlaceId?.trim()
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    final var status: RestaurantStatus = RestaurantStatus.ACTIVE
        private set

    init {
        require(this.brandName.isNotEmpty()) { "Restaurant brand name must not be blank" }
        require(this.kakaoPlaceId == null || this.kakaoPlaceId!!.isNotEmpty()) {
            "Kakao place ID must not be blank"
        }
    }

    fun linkKakaoPlaceId(kakaoPlaceId: String) {
        val normalized = kakaoPlaceId.trim()
        require(normalized.isNotEmpty()) { "Kakao place ID must not be blank" }
        check(this.kakaoPlaceId == null || this.kakaoPlaceId == normalized) {
            "Restaurant is already linked to another Kakao place"
        }
        this.kakaoPlaceId = normalized
    }

    fun rename(brandName: String) {
        check(status == RestaurantStatus.ACTIVE) { "Only an active restaurant can be renamed" }
        val displayName = RestaurantNormalization.displayText(brandName)
        require(displayName.isNotEmpty()) { "Restaurant brand name must not be blank" }
        this.brandName = displayName
    }

    fun close() {
        check(status == RestaurantStatus.ACTIVE) { "Only an active restaurant can be closed" }
        status = RestaurantStatus.CLOSED
    }

    fun reopen() {
        check(status == RestaurantStatus.CLOSED) { "Only a closed restaurant can be reopened" }
        status = RestaurantStatus.ACTIVE
    }

    fun relinkPickupLocation(pickupLocation: PickupLocation) {
        check(status == RestaurantStatus.ACTIVE) { "Only an active restaurant can be relinked" }
        require(this.pickupLocation !== pickupLocation) {
            "Restaurant is already linked to the pickup location"
        }
        this.pickupLocation = pickupLocation
    }
}
