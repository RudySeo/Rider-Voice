package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.PlaceCandidate
import com.ridervoice.api.restaurant.application.model.RegisterRestaurantCommand
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateResult
import com.ridervoice.api.restaurant.application.model.RestaurantRegistrationResult
import com.ridervoice.api.restaurant.application.model.RestaurantSearchQuery
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantUseCase
import com.ridervoice.api.restaurant.application.port.out.KakaoLocalPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.Restaurant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val kakaoLocalPort: KakaoLocalPort,
) : RestaurantUseCase {

    override fun search(query: RestaurantSearchQuery): RestaurantSearchResult {
        val internalRestaurants = restaurantRepository.searchByNameOrAddress(query.query)
        val internalByKakaoPlaceId = internalRestaurants.associateBy { it.kakaoPlaceId }
        val candidatesByKakaoPlaceId = linkedMapOf<String, RestaurantCandidateResult>()

        internalRestaurants.forEach { restaurant ->
            candidatesByKakaoPlaceId[restaurant.kakaoPlaceId] = restaurant.toCandidateResult()
        }

        val mergedKakaoPlaceIds = mutableSetOf<String>()
        kakaoLocalPort.searchByKeyword(query.query).forEach { candidate ->
            if (mergedKakaoPlaceIds.add(candidate.kakaoPlaceId)) {
                val registeredRestaurant =
                    internalByKakaoPlaceId[candidate.kakaoPlaceId]
                        ?: restaurantRepository.findByKakaoPlaceId(candidate.kakaoPlaceId)
                candidatesByKakaoPlaceId[candidate.kakaoPlaceId] =
                    candidate.toCandidateResult(registeredRestaurant)
            }
        }

        return RestaurantSearchResult(candidatesByKakaoPlaceId.values.toList())
    }

    override fun register(command: RegisterRestaurantCommand): RestaurantRegistrationResult {
        val verifiedCandidate = kakaoLocalPort.searchByKeyword(command.query)
            .firstOrNull { it.kakaoPlaceId == command.kakaoPlaceId }
            ?: throw ResourceNotFoundException("Selected Kakao place was not found in the repeated search")

        restaurantRepository.findByKakaoPlaceId(command.kakaoPlaceId)?.let {
            return it.toRegistrationResult()
        }

        val restaurant = Restaurant(
            kakaoPlaceId = verifiedCandidate.kakaoPlaceId,
            name = verifiedCandidate.name,
            address = verifiedCandidate.address,
            latitude = verifiedCandidate.latitude,
            longitude = verifiedCandidate.longitude,
        )

        val registeredRestaurant = try {
            restaurantRepository.save(restaurant)
        } catch (exception: DataIntegrityViolationException) {
            restaurantRepository.findByKakaoPlaceId(command.kakaoPlaceId) ?: throw exception
        }

        return registeredRestaurant.toRegistrationResult()
    }

    private fun Restaurant.toCandidateResult() = RestaurantCandidateResult(
        restaurantId = id,
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

    private fun PlaceCandidate.toCandidateResult(registeredRestaurant: Restaurant?) =
        RestaurantCandidateResult(
            restaurantId = registeredRestaurant?.id,
            kakaoPlaceId = kakaoPlaceId,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
        )

    private fun Restaurant.toRegistrationResult() = RestaurantRegistrationResult(
        restaurantId = id,
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )
}
