package com.ridervoice.api.restaurant.application

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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class RestaurantApplicationContractTest {

    @Test
    fun `registration command accepts only original query and selected Kakao place id`() {
        val command = RegisterRestaurantCommand(
            query = "강남 분식",
            kakaoPlaceId = "1234567890",
        )

        assertThat(command.query).isEqualTo("강남 분식")
        assertThat(command.kakaoPlaceId).isEqualTo("1234567890")
        assertThat(RegisterRestaurantCommand::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("query", "kakaoPlaceId")
    }

    @Test
    fun `ports exchange application models and domain restaurants`() {
        val place = PlaceCandidate(
            kakaoPlaceId = "1234567890",
            name = "라이더보이스 강남점",
            address = "서울 강남구 테헤란로 1",
            latitude = BigDecimal("37.4987654"),
            longitude = BigDecimal("127.0276543"),
        )
        val restaurantId = UUID.randomUUID()
        val useCase = StubRestaurantUseCase(place, restaurantId)
        val kakaoLocalPort = StubKakaoLocalPort(place)

        assertThat(kakaoLocalPort.searchByKeyword("강남 분식")).containsExactly(place)
        assertThat(useCase.search(RestaurantSearchQuery("강남 분식")).candidates)
            .containsExactly(
                RestaurantCandidateResult(
                    restaurantId = null,
                    kakaoPlaceId = place.kakaoPlaceId,
                    name = place.name,
                    address = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                ),
            )
        assertThat(
            useCase.register(
                RegisterRestaurantCommand(
                    query = "강남 분식",
                    kakaoPlaceId = place.kakaoPlaceId,
                ),
            ).restaurantId,
        ).isEqualTo(restaurantId)
    }

    @Suppress("unused")
    private fun repositoryContract(repository: RestaurantRepository, restaurant: Restaurant) {
        repository.searchByNameOrAddress("강남")
        repository.findById(restaurant.id)
        repository.findByKakaoPlaceId(restaurant.kakaoPlaceId)
        repository.save(restaurant)
    }

    private class StubKakaoLocalPort(
        private val place: PlaceCandidate,
    ) : KakaoLocalPort {
        override fun searchByKeyword(query: String): List<PlaceCandidate> = listOf(place)
    }

    private class StubRestaurantUseCase(
        private val place: PlaceCandidate,
        private val restaurantId: UUID,
    ) : RestaurantUseCase {
        override fun search(query: RestaurantSearchQuery) = RestaurantSearchResult(
            candidates = listOf(
                RestaurantCandidateResult(
                    restaurantId = null,
                    kakaoPlaceId = place.kakaoPlaceId,
                    name = place.name,
                    address = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                ),
            ),
        )

        override fun register(command: RegisterRestaurantCommand) = RestaurantRegistrationResult(
            restaurantId = restaurantId,
            kakaoPlaceId = place.kakaoPlaceId,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
        )
    }
}
