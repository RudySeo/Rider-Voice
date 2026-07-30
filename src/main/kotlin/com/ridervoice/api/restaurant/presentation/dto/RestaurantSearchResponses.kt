package com.ridervoice.api.restaurant.presentation.dto

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class RestaurantSearchResponse(
    val externalSearchStatus: ExternalSearchStatus,
    @field:Size(max = 20)
    val candidates: List<RestaurantSearchCandidateResponse>,
)

data class RestaurantSearchCandidateResponse(
    val candidateType: RestaurantCandidateType,
    @field:Schema(format = "int64", nullable = true)
    val restaurantId: Long?,
    @field:Schema(nullable = true)
    val kakaoPlaceId: String?,
    val name: String,
    val address: String,
    val aggregationStatus: AggregationStatus,
    val contributorCount: Int,
)

data class AddressSearchResponse(
    val query: String,
    @field:Size(max = 20)
    val candidates: List<AddressSearchCandidateResponse>,
)

data class AddressSearchCandidateResponse(
    val standardAddress: String,
    @field:Schema(nullable = true)
    val lotNumberAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    @field:Schema(format = "int64", nullable = true)
    val existingPickupLocationId: Long?,
)
