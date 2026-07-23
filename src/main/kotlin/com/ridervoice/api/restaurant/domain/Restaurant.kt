package com.ridervoice.api.restaurant.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

@Entity
@Table(
    name = "restaurants",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_restaurants_kakao_place_id",
            columnNames = ["kakao_place_id"],
        ),
    ],
)
class Restaurant(
    @field:Column(name = "kakao_place_id", nullable = false, updatable = false, length = 100)
    val kakaoPlaceId: String,
    @field:Column(nullable = false, length = 255)
    val name: String,
    @field:Column(nullable = false, length = 500)
    val address: String,
    @field:Column(nullable = false, precision = 10, scale = 7)
    val latitude: BigDecimal,
    @field:Column(nullable = false, precision = 11, scale = 7)
    val longitude: BigDecimal,
) : BaseEntity() {

    init {
        require(kakaoPlaceId.isNotBlank()) { "Kakao place id must not be blank" }
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
        require(address.isNotBlank()) { "Restaurant address must not be blank" }
        require(latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE) { "Latitude must be between -90 and 90" }
        require(longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE) {
            "Longitude must be between -180 and 180"
        }
    }

    private companion object {
        val MIN_LATITUDE = BigDecimal("-90")
        val MAX_LATITUDE = BigDecimal("90")
        val MIN_LONGITUDE = BigDecimal("-180")
        val MAX_LONGITUDE = BigDecimal("180")
    }
}
