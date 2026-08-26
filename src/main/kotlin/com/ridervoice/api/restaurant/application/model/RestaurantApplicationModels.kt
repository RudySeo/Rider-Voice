package com.ridervoice.api.restaurant.application.model

import java.math.BigDecimal
import com.ridervoice.api.restaurant.domain.RestaurantStatus

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
    val kakaoPlaceId: String?,
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
    val kakaoPlaceId: String?,
    val name: String,
    val address: String,
)

data class StoredRestaurantDetail(
    val restaurantId: Long,
    val name: String,
    val pickupLocationId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val status: RestaurantStatus = RestaurantStatus.ACTIVE,
)

data class PublicRestaurantPickupLocationResult(
    val pickupLocationId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class RestaurantAggregateMetricResult(
    val observedCount: Int,
    val notObservedCount: Int,
    val distribution: Map<String, BigDecimal>,
    val score: BigDecimal? = null,
)

data class RestaurantBrandReportMetrics(
    val packagingStability: RestaurantAggregateMetricResult,
    val orderReadiness: RestaurantAggregateMetricResult,
    val handoffAccuracy: RestaurantAggregateMetricResult,
)

data class RestaurantPickupLocationReportMetrics(
    val pickupSpaceCleanliness: RestaurantAggregateMetricResult,
    val staffInteraction: RestaurantAggregateMetricResult,
    val riderRespect: RestaurantAggregateMetricResult,
)

data class RestaurantBrandReportResult(
    val status: AggregationStatus,
    val contributorCount: Int,
    val metrics: RestaurantBrandReportMetrics?,
)

data class RestaurantPickupLocationReportResult(
    val status: AggregationStatus,
    val contributorCount: Int,
    val metrics: RestaurantPickupLocationReportMetrics?,
)

data class PublicRestaurantDetailResult(
    val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus = RestaurantStatus.ACTIVE,
    val pickupLocation: PublicRestaurantPickupLocationResult,
    val brandReport: RestaurantBrandReportResult,
    val pickupLocationReport: RestaurantPickupLocationReportResult,
    val verificationStatus: String,
    val verificationNotice: String,
)

data class ExternalRestaurantCandidate(
    val kakaoPlaceId: String,
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
