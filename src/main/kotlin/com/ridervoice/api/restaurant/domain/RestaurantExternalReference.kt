package com.ridervoice.api.restaurant.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "restaurant_external_references")
class RestaurantExternalReference(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "restaurant_id", nullable = false, updatable = false)
    val restaurant: Restaurant,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 20)
    val provider: RestaurantExternalProvider,
    externalPlaceId: String,
) : BaseEntity() {

    @field:Column(name = "external_place_id", nullable = false, updatable = false, length = 255)
    val externalPlaceId: String = externalPlaceId.trim()

    init {
        require(this.externalPlaceId.isNotEmpty()) { "External place ID must not be blank" }
    }
}
