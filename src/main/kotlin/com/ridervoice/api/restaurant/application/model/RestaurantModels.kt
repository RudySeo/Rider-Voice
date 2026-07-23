package com.ridervoice.api.restaurant.application.model

import java.math.BigDecimal
import java.util.UUID

data class RestaurantSearchQuery(
    val query: String,
)

data class RegisterRestaurantCommand(
    val query: String,
    val kakaoPlaceId: String,
)

data class PlaceCandidate(
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class RestaurantCandidateResult(
    val restaurantId: UUID?,
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class RestaurantSearchResult(
    val candidates: List<RestaurantCandidateResult>,
)

data class RestaurantRegistrationResult(
    val restaurantId: UUID,
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)
