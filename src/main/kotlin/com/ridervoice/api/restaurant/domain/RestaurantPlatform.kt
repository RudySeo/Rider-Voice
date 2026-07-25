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

@Entity
@Table(
    name = "restaurant_platforms",
    indexes = [
        Index(
            name = "idx_restaurant_platforms_restaurant",
            columnList = "restaurant_id",
        ),
    ],
)
class RestaurantPlatform(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "restaurant_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_restaurant_platforms_restaurant"),
    )
    val restaurant: Restaurant,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 32)
    val platform: DeliveryPlatform,
) : BaseEntity()
