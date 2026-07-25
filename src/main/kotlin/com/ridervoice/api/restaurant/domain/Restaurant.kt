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
            name = "uk_restaurants_pickup_location_normalized_name",
            columnNames = ["pickup_location_id", "normalized_name"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_restaurants_status_normalized_name",
            columnList = "status, normalized_name",
        ),
        Index(
            name = "idx_restaurants_canonical",
            columnList = "canonical_restaurant_id",
        ),
    ],
)
class Restaurant(
    brandName: String,
    pickupLocation: PickupLocation,
) : BaseEntity() {

    @field:Column(name = "brand_name", nullable = false, length = 255)
    val brandName: String = RestaurantNormalization.displayText(brandName)

    @field:Column(name = "normalized_name", nullable = false, length = 255)
    val normalizedName: String = RestaurantNormalization.normalizedText(brandName)

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "pickup_location_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_restaurants_pickup_location"),
    )
    final var pickupLocation: PickupLocation = pickupLocation
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    final var status: RestaurantStatus = RestaurantStatus.ACTIVE
        private set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(
        name = "canonical_restaurant_id",
        foreignKey = ForeignKey(name = "fk_restaurants_canonical_restaurant"),
    )
    final var canonicalRestaurant: Restaurant? = null
        private set

    init {
        require(this.brandName.isNotEmpty()) { "Restaurant brand name must not be blank" }
    }

    fun mergeInto(canonicalRestaurant: Restaurant) {
        check(status == RestaurantStatus.ACTIVE) { "Only an active restaurant can be merged" }
        require(canonicalRestaurant !== this) { "Restaurant cannot be merged into itself" }
        require(canonicalRestaurant.status == RestaurantStatus.ACTIVE) {
            "Canonical restaurant must be active"
        }

        this.canonicalRestaurant = canonicalRestaurant
        status = RestaurantStatus.MERGED
    }
}
