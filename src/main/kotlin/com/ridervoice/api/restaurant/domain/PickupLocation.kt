package com.ridervoice.api.restaurant.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "pickup_locations")
class PickupLocation(
    standardAddress: String,
    detailAddress: String?,
    @field:Column(nullable = false, precision = 11, scale = 8)
    val latitude: BigDecimal,
    @field:Column(nullable = false, precision = 11, scale = 8)
    val longitude: BigDecimal,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 32)
    val source: PickupLocationSource,
) : BaseEntity() {

    @field:Column(name = "standard_address", nullable = false, updatable = false, length = 255)
    val standardAddress: String = RestaurantNormalization.displayText(standardAddress)

    @field:Column(name = "normalized_address", nullable = false, updatable = false, length = 255)
    val normalizedAddress: String = RestaurantNormalization.normalizedText(standardAddress)

    @field:Column(name = "detail_address", updatable = false, length = 255)
    val detailAddress: String? = RestaurantNormalization.optionalDisplayText(detailAddress)

    @field:Column(name = "location_key", nullable = false, updatable = false, length = 600)
    val locationKey: String = RestaurantNormalization.locationKey(
        normalizedAddress = normalizedAddress,
        normalizedDetailAddress = this.detailAddress?.let(RestaurantNormalization::normalizedText),
    )

    init {
        require(this.standardAddress.isNotEmpty()) { "Standard address must not be blank" }
        require(latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE) {
            "Latitude must be between -90 and 90"
        }
        require(longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE) {
            "Longitude must be between -180 and 180"
        }
    }

    private companion object {
        val MIN_LATITUDE: BigDecimal = BigDecimal("-90")
        val MAX_LATITUDE: BigDecimal = BigDecimal("90")
        val MIN_LONGITUDE: BigDecimal = BigDecimal("-180")
        val MAX_LONGITUDE: BigDecimal = BigDecimal("180")
    }
}
