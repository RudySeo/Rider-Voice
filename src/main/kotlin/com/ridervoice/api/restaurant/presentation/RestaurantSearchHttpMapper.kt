package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.restaurant.application.model.AddressSearchResult
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.presentation.dto.AddressSearchCandidateResponse
import com.ridervoice.api.restaurant.presentation.dto.AddressSearchResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchCandidateResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantSearchResponse
import com.ridervoice.api.restaurant.presentation.dto.SearchQueryRequest
import org.springframework.stereotype.Component

@Component
class RestaurantSearchHttpMapper {
    fun toRestaurantSearchCommand(request: SearchQueryRequest) = SearchRestaurantsCommand(request.query)

    fun toAddressSearchCommand(userId: Long, request: SearchQueryRequest) =
        SearchAddressesCommand(userId, request.query)

    fun toRestaurantSearchResponse(result: RestaurantSearchResult) = RestaurantSearchResponse(
        externalSearchStatus = result.externalSearchStatus,
        candidates = result.candidates.map { candidate ->
            RestaurantSearchCandidateResponse(
                candidateType = candidate.candidateType,
                restaurantId = candidate.restaurantId,
                kakaoPlaceId = candidate.kakaoPlaceId,
                name = candidate.name,
                address = candidate.address,
                aggregationStatus = candidate.aggregationStatus,
                contributorCount = candidate.contributorCount,
            )
        },
    )

    fun toAddressSearchResponse(result: AddressSearchResult) = AddressSearchResponse(
        query = result.query,
        candidates = result.candidates.map { candidate ->
            AddressSearchCandidateResponse(
                standardAddress = candidate.standardAddress,
                lotNumberAddress = candidate.lotNumberAddress,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                existingPickupLocationId = candidate.existingPickupLocationId,
            )
        },
    )
}
