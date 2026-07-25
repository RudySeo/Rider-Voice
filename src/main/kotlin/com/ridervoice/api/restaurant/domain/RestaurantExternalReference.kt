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
    name = "restaurant_external_references",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_restaurant_external_references_provider_place",
            columnNames = ["provider", "external_place_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_restaurant_external_references_restaurant",
            columnList = "restaurant_id",
        ),
    ],
)
class RestaurantExternalReference(
    restaurant: Restaurant,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 20)
    val provider: RestaurantExternalProvider,
    externalPlaceId: String,
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "restaurant_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_restaurant_external_references_restaurant"),
    )
    final var restaurant: Restaurant = restaurant
        private set

    @field:Column(name = "external_place_id", nullable = false, updatable = false, length = 255)
    val externalPlaceId: String = externalPlaceId.trim()

    init {
        require(this.externalPlaceId.isNotEmpty()) { "External place ID must not be blank" }
    }

    fun relinkToRestaurant(restaurant: Restaurant) {
        this.restaurant = restaurant
    }
}
