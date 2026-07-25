package com.ridervoice.api.restaurant.application.model

import java.math.BigDecimal

enum class ExternalSearchStatus {
    AVAILABLE,
    UNAVAILABLE,
}

enum class RestaurantCandidateType {
    INTERNAL,
    KAKAO,
}

enum class AggregationStatus {
    NO_REVIEWS,
    COLLECTING,
    PUBLISHED,
}

data class RestaurantSearchResult(
    val externalSearchStatus: ExternalSearchStatus,
    val candidates: List<RestaurantSearchCandidate>,
)

data class RestaurantSearchCandidate(
    val candidateType: RestaurantCandidateType,
    val restaurantId: Long?,
    val externalPlaceId: String?,
    val name: String,
    val address: String,
    val aggregationStatus: AggregationStatus,
    val contributorCount: Int,
)

data class AddressSearchResult(
    val query: String,
    val candidates: List<AddressSearchCandidate>,
)

data class AddressSearchCandidate(
    val standardAddress: String,
    val lotNumberAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val existingPickupLocationId: Long?,
)

data class StoredRestaurantSearchCandidate(
    val restaurantId: Long,
    val externalPlaceId: String?,
    val name: String,
    val address: String,
)

data class ExternalRestaurantCandidate(
    val externalPlaceId: String,
    val name: String,
    val standardAddress: String,
    val lotNumberAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class ExternalAddressCandidate(
    val standardAddress: String,
    val lotNumberAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

sealed interface ProviderSearchResult<out T> {
    data class Available<T>(val candidates: List<T>) : ProviderSearchResult<T>

    data class Unavailable(val reason: ProviderFailureReason) : ProviderSearchResult<Nothing>
}

enum class ProviderFailureReason {
    CONNECTION_FAILED,
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    INVALID_RESPONSE,
}
