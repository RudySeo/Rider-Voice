package com.ridervoice.api.restaurant.application.model

import java.math.BigDecimal

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
    val restaurantId: Long?,
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
    val restaurantId: Long,
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)
