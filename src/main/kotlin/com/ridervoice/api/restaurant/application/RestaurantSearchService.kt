package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ExternalProviderUnavailableException
import com.ridervoice.api.restaurant.application.model.AddressSearchCandidate
import com.ridervoice.api.restaurant.application.model.AddressSearchResult
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
import com.ridervoice.api.restaurant.application.model.RestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.model.StoredLinkedRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.PublicKakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantSearchLinkQuery
import com.ridervoice.api.restaurant.application.port.out.RestaurantSearchReviewSummaryProvider
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import org.springframework.stereotype.Service

@Service
class RestaurantSearchService(
    private val restaurants: RestaurantRepository,
    private val pickupLocations: PickupLocationRepository,
    private val keywordSearch: PublicKakaoKeywordSearchPort,
    private val addressSearch: KakaoAddressSearchPort,
    private val linkedRestaurants: RestaurantSearchLinkQuery,
    private val reviewSummaries: RestaurantSearchReviewSummaryProvider,
) : SearchRestaurantsUseCase, SearchAddressesUseCase {

    override fun search(command: SearchRestaurantsCommand): RestaurantSearchResult {
        val query = RestaurantNormalization.displayText(command.query)
        val storedCandidates = restaurants.searchActive(query, SEARCH_LIMIT)
        val internalCandidates = storedCandidates.map(::internalCandidate).toMutableList()
        val includedRestaurantIds = storedCandidates.mapTo(linkedSetOf()) { it.restaurantId }
        val includedKakaoPlaceIds = storedCandidates.mapNotNullTo(linkedSetOf()) { it.kakaoPlaceId }

        return when (val externalResult = keywordSearch.search(query, SEARCH_LIMIT)) {
            is ProviderSearchResult.Unavailable -> RestaurantSearchResult(
                externalSearchStatus = ExternalSearchStatus.UNAVAILABLE,
                candidates = withReviewSummaries(internalCandidates.take(SEARCH_LIMIT)),
            )

            is ProviderSearchResult.Available -> {
                val linkedCandidates = linkedRestaurants.findByKakaoPlaceIds(
                    externalResult.candidates.mapTo(linkedSetOf(), ExternalRestaurantCandidate::kakaoPlaceId),
                )
                externalResult.candidates.forEach { external ->
                    if (internalCandidates.size >= SEARCH_LIMIT ||
                        !includedKakaoPlaceIds.add(external.kakaoPlaceId)
                    ) {
                        return@forEach
                    }

                    val linkedInternal = when (val linked = linkedCandidates[external.kakaoPlaceId]) {
                        null -> {
                            internalCandidates += externalCandidate(external)
                            return@forEach
                        }
                        else -> {
                            if (linked.status != RestaurantStatus.ACTIVE) return@forEach
                            internalCandidate(linked)
                        }
                    }
                    run {
                        val linkedRestaurantId = linkedInternal.restaurantId!!
                        if (includedRestaurantIds.add(linkedRestaurantId)) {
                            internalCandidates += linkedInternal
                        } else {
                            val existingIndex = internalCandidates.indexOfFirst {
                                it.restaurantId == linkedRestaurantId
                            }
                            if (existingIndex >= 0 && internalCandidates[existingIndex].kakaoPlaceId == null) {
                                internalCandidates[existingIndex] = internalCandidates[existingIndex].copy(
                                    kakaoPlaceId = external.kakaoPlaceId,
                                )
                            }
                        }
                    }
                }
                RestaurantSearchResult(
                    externalSearchStatus = ExternalSearchStatus.AVAILABLE,
                    candidates = withReviewSummaries(internalCandidates.take(SEARCH_LIMIT)),
                )
            }
        }
    }

    override fun search(command: SearchAddressesCommand): AddressSearchResult {
        val query = RestaurantNormalization.displayText(command.query)
        val candidates = when (val result = addressSearch.search(query, SEARCH_LIMIT)) {
            is ProviderSearchResult.Unavailable -> throw ExternalProviderUnavailableException(
                "Address verification provider is unavailable: ${result.reason}",
            )
            is ProviderSearchResult.Available -> result.candidates
        }

        return AddressSearchResult(
            query = query,
            candidates = candidates.take(SEARCH_LIMIT).map { candidate ->
                val location = PickupLocation(
                    standardAddress = candidate.standardAddress,
                    detailAddress = null,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    source = PickupLocationSource.MANUAL_ADDRESS,
                )
                AddressSearchCandidate(
                    standardAddress = location.standardAddress,
                    lotNumberAddress = candidate.lotNumberAddress,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    existingPickupLocationId = externalLocationId(location),
                )
            },
        )
    }

    private fun externalLocationId(location: PickupLocation): Long? =
        pickupLocations.findByLocationKey(location.locationKey)?.id

    private fun withReviewSummaries(
        candidates: List<RestaurantSearchCandidate>,
    ): List<RestaurantSearchCandidate> {
        val internalRestaurantIds = candidates.mapNotNullTo(linkedSetOf()) { candidate ->
            candidate.restaurantId.takeIf { candidate.candidateType == RestaurantCandidateType.INTERNAL }
        }
        if (internalRestaurantIds.isEmpty()) return candidates

        val summaries = reviewSummaries.findByRestaurantIds(internalRestaurantIds)
        return candidates.map { candidate ->
            val summary = candidate.restaurantId?.let(summaries::get)
            if (candidate.candidateType != RestaurantCandidateType.INTERNAL || summary == null) {
                candidate
            } else {
                candidate.copy(
                    aggregationStatus = summary.status,
                    contributorCount = summary.contributorCount,
                )
            }
        }
    }

    private fun internalCandidate(stored: StoredRestaurantSearchCandidate) = RestaurantSearchCandidate(
        candidateType = RestaurantCandidateType.INTERNAL,
        restaurantId = stored.restaurantId,
        kakaoPlaceId = stored.kakaoPlaceId,
        name = stored.name,
        address = stored.address,
        aggregationStatus = AggregationStatus.NO_REVIEWS,
        contributorCount = 0,
    )

    private fun internalCandidate(stored: StoredLinkedRestaurantSearchCandidate) = RestaurantSearchCandidate(
        candidateType = RestaurantCandidateType.INTERNAL,
        restaurantId = stored.restaurantId,
        kakaoPlaceId = stored.kakaoPlaceId,
        name = stored.name,
        address = stored.address,
        aggregationStatus = AggregationStatus.NO_REVIEWS,
        contributorCount = 0,
    )

    private fun externalCandidate(external: ExternalRestaurantCandidate) = RestaurantSearchCandidate(
        candidateType = RestaurantCandidateType.KAKAO,
        restaurantId = null,
        kakaoPlaceId = external.kakaoPlaceId,
        name = external.name,
        address = external.standardAddress,
        aggregationStatus = AggregationStatus.NO_REVIEWS,
        contributorCount = 0,
    )

    private companion object {
        const val SEARCH_LIMIT = 20
    }

}
