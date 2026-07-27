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
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.PublicKakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantExternalReferenceRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import org.springframework.stereotype.Service

@Service
class RestaurantSearchService(
    private val restaurants: RestaurantRepository,
    private val pickupLocations: PickupLocationRepository,
    private val externalReferences: RestaurantExternalReferenceRepository,
    private val keywordSearch: PublicKakaoKeywordSearchPort,
    private val addressSearch: KakaoAddressSearchPort,
) : SearchRestaurantsUseCase, SearchAddressesUseCase {

    override fun search(command: SearchRestaurantsCommand): RestaurantSearchResult {
        val query = RestaurantNormalization.displayText(command.query)
        val storedCandidates = restaurants.searchActive(query, SEARCH_LIMIT)
        val internalCandidates = storedCandidates.map(::internalCandidate).toMutableList()
        val includedRestaurantIds = storedCandidates.mapTo(linkedSetOf()) { it.restaurantId }
        val includedExternalIds = storedCandidates.mapNotNullTo(linkedSetOf()) { it.externalPlaceId }

        return when (val externalResult = keywordSearch.search(query, SEARCH_LIMIT)) {
            is ProviderSearchResult.Unavailable -> RestaurantSearchResult(
                externalSearchStatus = ExternalSearchStatus.UNAVAILABLE,
                candidates = internalCandidates.take(SEARCH_LIMIT),
            )

            is ProviderSearchResult.Available -> {
                externalResult.candidates.forEach { external ->
                    if (internalCandidates.size >= SEARCH_LIMIT || !includedExternalIds.add(external.externalPlaceId)) {
                        return@forEach
                    }

                    val linkedInternal = when (val linked = findLinkedInternalCandidate(external)) {
                        LinkedExternalCandidate.Suppressed -> return@forEach
                        LinkedExternalCandidate.Unlinked -> {
                            internalCandidates += externalCandidate(external)
                            return@forEach
                        }
                        is LinkedExternalCandidate.Found -> linked.candidate
                    }
                    run {
                        val linkedRestaurantId = linkedInternal.restaurantId!!
                        if (includedRestaurantIds.add(linkedRestaurantId)) {
                            internalCandidates += linkedInternal
                        } else {
                            val existingIndex = internalCandidates.indexOfFirst {
                                it.restaurantId == linkedRestaurantId
                            }
                            if (existingIndex >= 0 && internalCandidates[existingIndex].externalPlaceId == null) {
                                internalCandidates[existingIndex] = internalCandidates[existingIndex].copy(
                                    externalPlaceId = external.externalPlaceId,
                                )
                            }
                        }
                    }
                }
                RestaurantSearchResult(
                    externalSearchStatus = ExternalSearchStatus.AVAILABLE,
                    candidates = internalCandidates.take(SEARCH_LIMIT),
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

    private fun findLinkedInternalCandidate(
        external: ExternalRestaurantCandidate,
    ): LinkedExternalCandidate {
        val reference = externalReferences.findByProviderAndExternalPlaceId(
            RestaurantExternalProvider.KAKAO,
            external.externalPlaceId,
        ) ?: return LinkedExternalCandidate.Unlinked
        val canonical = restaurants.findCanonicalById(reference.restaurant.id)
            ?: return LinkedExternalCandidate.Suppressed
        val stored = restaurants.findSearchCandidateById(canonical.id)
            ?: return LinkedExternalCandidate.Suppressed
        return LinkedExternalCandidate.Found(
            internalCandidate(stored.copy(externalPlaceId = external.externalPlaceId)),
        )
    }

    private fun externalLocationId(location: PickupLocation): Long? =
        pickupLocations.findByLocationKey(location.locationKey)?.id

    private fun internalCandidate(stored: StoredRestaurantSearchCandidate) = RestaurantSearchCandidate(
        candidateType = RestaurantCandidateType.INTERNAL,
        restaurantId = stored.restaurantId,
        externalPlaceId = stored.externalPlaceId,
        name = stored.name,
        address = stored.address,
        aggregationStatus = AggregationStatus.NO_REVIEWS,
        contributorCount = 0,
    )

    private fun externalCandidate(external: ExternalRestaurantCandidate) = RestaurantSearchCandidate(
        candidateType = RestaurantCandidateType.KAKAO,
        restaurantId = null,
        externalPlaceId = external.externalPlaceId,
        name = external.name,
        address = external.standardAddress,
        aggregationStatus = AggregationStatus.NO_REVIEWS,
        contributorCount = 0,
    )

    private companion object {
        const val SEARCH_LIMIT = 20
    }

    private sealed interface LinkedExternalCandidate {
        data class Found(val candidate: RestaurantSearchCandidate) : LinkedExternalCandidate
        data object Suppressed : LinkedExternalCandidate
        data object Unlinked : LinkedExternalCandidate
    }
}
