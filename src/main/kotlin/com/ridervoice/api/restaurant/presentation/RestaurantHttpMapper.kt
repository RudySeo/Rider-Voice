package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.restaurant.application.model.RegisterRestaurantCommand
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateResult
import com.ridervoice.api.restaurant.application.model.RestaurantRegistrationResult
import com.ridervoice.api.restaurant.application.model.RestaurantSearchQuery
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.presentation.dto.CreateRestaurantRequest
import com.ridervoice.api.restaurant.presentation.dto.RestaurantCandidateResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantRegistrationResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchRequest
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchResponse

fun RestaurantSearchRequest.toQuery() = RestaurantSearchQuery(query = query)

fun CreateRestaurantRequest.toCommand() = RegisterRestaurantCommand(
    query = query,
    kakaoPlaceId = kakaoPlaceId,
)

fun RestaurantSearchResult.toResponse() = RestaurantSearchResponse(
    candidates = candidates.map(RestaurantCandidateResult::toResponse),
)

private fun RestaurantCandidateResult.toResponse() = RestaurantCandidateResponse(
    restaurantId = restaurantId,
    kakaoPlaceId = kakaoPlaceId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
)

fun RestaurantRegistrationResult.toResponse() = RestaurantRegistrationResponse(
    restaurantId = restaurantId,
    kakaoPlaceId = kakaoPlaceId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
)
