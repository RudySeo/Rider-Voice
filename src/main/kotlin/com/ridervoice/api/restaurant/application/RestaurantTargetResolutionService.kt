package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.BadRequestException
import com.ridervoice.api.common.error.ExternalProviderUnavailableException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ResolveRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ResolvedRestaurantTargetResult
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantExternalReferenceRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantPlatformRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class RestaurantTargetResolutionService(
    private val restaurantRepository: RestaurantRepository,
    private val keywordSearch: KakaoKeywordSearchPort,
    private val addressSearch: KakaoAddressSearchPort,
    private val targetWriter: RestaurantTargetWriter,
) : ResolveRestaurantTargetUseCase {

    override fun resolve(command: RestaurantTargetCommand): ResolvedRestaurantTargetResult = when (command) {
        is ExistingRestaurantTargetCommand -> resolveExisting(command)
        is KakaoRestaurantTargetCommand -> resolveKakao(command)
        is ManualExistingLocationRestaurantTargetCommand -> retryUniqueCollision {
            targetWriter.resolveManualExistingLocation(command)
        }
        is ManualAddressRestaurantTargetCommand -> resolveManualAddress(command)
    }

    private fun resolveExisting(command: ExistingRestaurantTargetCommand): ResolvedRestaurantTargetResult {
        val restaurant = restaurantRepository.findCanonicalById(command.restaurantId)
            ?: throw ResourceNotFoundException("Restaurant target was not found")
        return ResolvedRestaurantTargetResult(restaurant.id)
    }

    private fun resolveKakao(command: KakaoRestaurantTargetCommand): ResolvedRestaurantTargetResult {
        val query = RestaurantNormalization.displayText(command.query)
        val candidates = availableCandidates(keywordSearch.search(query, SEARCH_LIMIT), "Kakao place")
        val selected = candidates.firstOrNull { it.externalPlaceId == command.kakaoPlaceId.trim() }
            ?: throw BadRequestException("Selected Kakao place was not present in the repeated search")
        return retryUniqueCollision { targetWriter.resolveKakao(selected) }
    }

    private fun resolveManualAddress(
        command: ManualAddressRestaurantTargetCommand,
    ): ResolvedRestaurantTargetResult {
        val query = RestaurantNormalization.displayText(command.addressQuery)
        val candidates = availableCandidates(addressSearch.search(query, SEARCH_LIMIT), "Address")
        val selectedAddress = RestaurantNormalization.normalizedText(command.selectedStandardAddress)
        val selected = candidates.firstOrNull {
            RestaurantNormalization.normalizedText(it.standardAddress) == selectedAddress
        } ?: throw BadRequestException("Selected address was not present in the repeated search")
        return retryUniqueCollision { targetWriter.resolveManualAddress(command, selected) }
    }

    private fun <T> availableCandidates(result: ProviderSearchResult<T>, target: String): List<T> =
        when (result) {
            is ProviderSearchResult.Available -> result.candidates
            is ProviderSearchResult.Unavailable -> throw ExternalProviderUnavailableException(
                "$target verification provider is unavailable: ${result.reason}",
            )
        }

    private fun retryUniqueCollision(write: () -> ResolvedRestaurantTargetResult): ResolvedRestaurantTargetResult {
        var lastFailure: DataIntegrityViolationException? = null
        repeat(MAX_WRITE_ATTEMPTS) {
            try {
                return write()
            } catch (failure: DataIntegrityViolationException) {
                lastFailure = failure
            }
        }
        throw lastFailure ?: IllegalStateException("Restaurant target write did not run")
    }

    private companion object {
        const val SEARCH_LIMIT = 20
        const val MAX_WRITE_ATTEMPTS = 4
    }
}

internal interface RestaurantTargetWriter {
    fun resolveKakao(candidate: ExternalRestaurantCandidate): ResolvedRestaurantTargetResult

    fun resolveManualExistingLocation(
        command: ManualExistingLocationRestaurantTargetCommand,
    ): ResolvedRestaurantTargetResult

    fun resolveManualAddress(
        command: ManualAddressRestaurantTargetCommand,
        candidate: ExternalAddressCandidate,
    ): ResolvedRestaurantTargetResult
}

@Service
internal class TransactionalRestaurantTargetWriter(
    private val pickupLocations: PickupLocationRepository,
    private val restaurants: RestaurantRepository,
    private val externalReferences: RestaurantExternalReferenceRepository,
    private val platforms: RestaurantPlatformRepository,
) : RestaurantTargetWriter {

    @Transactional
    override fun resolveKakao(candidate: ExternalRestaurantCandidate): ResolvedRestaurantTargetResult {
        findByExternalReference(candidate.externalPlaceId)?.let { return it }

        val location = findOrCreateLocation(
            standardAddress = candidate.standardAddress,
            detailAddress = null,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            source = PickupLocationSource.KAKAO,
        )
        val restaurant = findOrCreateRestaurant(location, candidate.name)
        val reference = externalReferences.findByProviderAndExternalPlaceId(
            RestaurantExternalProvider.KAKAO,
            candidate.externalPlaceId,
        ) ?: externalReferences.save(
            RestaurantExternalReference(
                restaurant = canonicalRestaurant(restaurant),
                provider = RestaurantExternalProvider.KAKAO,
                externalPlaceId = candidate.externalPlaceId,
            ),
        )
        return canonicalResult(reference.restaurant)
    }

    @Transactional
    override fun resolveManualExistingLocation(
        command: ManualExistingLocationRestaurantTargetCommand,
    ): ResolvedRestaurantTargetResult {
        val location = pickupLocations.findById(command.pickupLocationId)
            ?: throw ResourceNotFoundException("Pickup location target was not found")
        val restaurant = canonicalRestaurant(findOrCreateRestaurant(location, command.name))
        addMissingPlatforms(restaurant, command.platforms)
        return ResolvedRestaurantTargetResult(restaurant.id)
    }

    @Transactional
    override fun resolveManualAddress(
        command: ManualAddressRestaurantTargetCommand,
        candidate: ExternalAddressCandidate,
    ): ResolvedRestaurantTargetResult {
        val location = findOrCreateLocation(
            standardAddress = candidate.standardAddress,
            detailAddress = command.detailAddress,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            source = PickupLocationSource.MANUAL_ADDRESS,
        )
        val restaurant = canonicalRestaurant(findOrCreateRestaurant(location, command.name))
        addMissingPlatforms(restaurant, command.platforms)
        return ResolvedRestaurantTargetResult(restaurant.id)
    }

    private fun findByExternalReference(externalPlaceId: String): ResolvedRestaurantTargetResult? =
        externalReferences.findByProviderAndExternalPlaceId(
            RestaurantExternalProvider.KAKAO,
            externalPlaceId,
        )?.let { canonicalResult(it.restaurant) }

    private fun findOrCreateLocation(
        standardAddress: String,
        detailAddress: String?,
        latitude: java.math.BigDecimal,
        longitude: java.math.BigDecimal,
        source: PickupLocationSource,
    ): PickupLocation {
        val candidate = PickupLocation(
            standardAddress = standardAddress,
            detailAddress = detailAddress,
            latitude = latitude,
            longitude = longitude,
            source = source,
        )
        return pickupLocations.findByLocationKey(candidate.locationKey) ?: pickupLocations.save(candidate)
    }

    private fun findOrCreateRestaurant(location: PickupLocation, name: String): Restaurant {
        val candidate = Restaurant(name, location)
        return restaurants.findByPickupLocationIdAndNormalizedName(location.id, candidate.normalizedName)
            ?: restaurants.save(candidate)
    }

    private fun canonicalRestaurant(restaurant: Restaurant): Restaurant =
        restaurants.findCanonicalById(restaurant.id)
            ?: throw ResourceNotFoundException("Canonical restaurant target was not found")

    private fun canonicalResult(restaurant: Restaurant): ResolvedRestaurantTargetResult =
        ResolvedRestaurantTargetResult(canonicalRestaurant(restaurant).id)

    private fun addMissingPlatforms(restaurant: Restaurant, requested: Set<DeliveryPlatform>) {
        val existing = platforms.findPlatforms(restaurant.id)
        val missing = requested - existing
        if (missing.isNotEmpty()) {
            platforms.saveAll(missing.map { platform -> RestaurantPlatform(restaurant, platform) })
        }
    }
}
