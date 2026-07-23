package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.PlaceCandidate
import com.ridervoice.api.restaurant.application.model.RegisterRestaurantCommand
import com.ridervoice.api.restaurant.application.model.RestaurantSearchQuery
import com.ridervoice.api.restaurant.application.port.out.KakaoLocalPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.Restaurant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal

class RestaurantServiceTest {

    private var nextRestaurantId = 1L

    @Test
    fun `search merges internal and Kakao candidates by place id and includes registration id`() {
        val registered = restaurant(
            kakaoPlaceId = "registered",
            name = "저장된 이름",
            address = "저장된 주소",
        )
        val internalOnly = restaurant(kakaoPlaceId = "internal-only", name = "내부 식당")
        val kakaoRegistered = place(
            kakaoPlaceId = registered.kakaoPlaceId,
            name = "카카오 최신 이름",
            address = "카카오 최신 주소",
        )
        val kakaoOnly = place(kakaoPlaceId = "kakao-only", name = "카카오 식당")
        val repository = FakeRestaurantRepository(
            searchResults = listOf(registered, internalOnly),
            restaurants = mutableMapOf(registered.kakaoPlaceId to registered),
        )
        val service = RestaurantService(
            restaurantRepository = repository,
            kakaoLocalPort = FakeKakaoLocalPort(
                results = listOf(kakaoRegistered, kakaoRegistered.copy(name = "중복 후보"), kakaoOnly),
            ),
        )

        val result = service.search(RestaurantSearchQuery("강남 분식"))

        assertThat(result.candidates).hasSize(3)
        assertThat(result.candidates.map { it.kakaoPlaceId })
            .containsExactly("registered", "internal-only", "kakao-only")
        val mergedRegistered = result.candidates.single { it.kakaoPlaceId == "registered" }
        assertThat(mergedRegistered.restaurantId).isEqualTo(registered.id)
        assertThat(mergedRegistered.name).isEqualTo(kakaoRegistered.name)
        assertThat(mergedRegistered.address).isEqualTo(kakaoRegistered.address)
        assertThat(result.candidates.single { it.kakaoPlaceId == "internal-only" }.restaurantId)
            .isEqualTo(internalOnly.id)
        assertThat(result.candidates.single { it.kakaoPlaceId == "kakao-only" }.restaurantId).isNull()
    }

    @Test
    fun `search returns an empty result when internal and Kakao searches are empty`() {
        val service = RestaurantService(
            restaurantRepository = FakeRestaurantRepository(),
            kakaoLocalPort = FakeKakaoLocalPort(),
        )

        val result = service.search(RestaurantSearchQuery("없는 음식점"))

        assertThat(result.candidates).isEmpty()
    }

    @Test
    fun `register rejects a place id not verified by repeating the original query`() {
        val kakaoLocal = FakeKakaoLocalPort(
            results = listOf(place(kakaoPlaceId = "other-place")),
        )
        val repository = FakeRestaurantRepository()
        val service = RestaurantService(repository, kakaoLocal)

        assertThatThrownBy {
            service.register(
                RegisterRestaurantCommand(
                    query = "원래 검색어",
                    kakaoPlaceId = "unverified-place",
                ),
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)

        assertThat(kakaoLocal.queries).containsExactly("원래 검색어")
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun `register returns an existing restaurant after verifying the selected place`() {
        val existing = restaurant(kakaoPlaceId = "selected-place", name = "기존 이름")
        val repository = FakeRestaurantRepository(
            restaurants = mutableMapOf(existing.kakaoPlaceId to existing),
        )
        val kakaoLocal = FakeKakaoLocalPort(
            results = listOf(place(kakaoPlaceId = existing.kakaoPlaceId, name = "provider 이름")),
        )
        val service = RestaurantService(repository, kakaoLocal)

        val result = service.register(
            RegisterRestaurantCommand(
                query = "원래 검색어",
                kakaoPlaceId = existing.kakaoPlaceId,
            ),
        )

        assertThat(kakaoLocal.queries).containsExactly("원래 검색어")
        assertThat(result.restaurantId).isEqualTo(existing.id)
        assertThat(result.name).isEqualTo(existing.name)
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun `register creates a restaurant only from the verified provider candidate`() {
        val verified = place(
            kakaoPlaceId = "selected-place",
            name = "provider 이름",
            address = "provider 주소",
            latitude = BigDecimal("35.1795543"),
            longitude = BigDecimal("129.0756416"),
        )
        val repository = FakeRestaurantRepository()
        val service = RestaurantService(
            restaurantRepository = repository,
            kakaoLocalPort = FakeKakaoLocalPort(results = listOf(verified)),
        )

        val result = service.register(
            RegisterRestaurantCommand(
                query = "원래 검색어",
                kakaoPlaceId = verified.kakaoPlaceId,
            ),
        )

        assertThat(repository.saved).hasSize(1)
        val saved = repository.saved.single()
        assertThat(saved.kakaoPlaceId).isEqualTo(verified.kakaoPlaceId)
        assertThat(saved.name).isEqualTo(verified.name)
        assertThat(saved.address).isEqualTo(verified.address)
        assertThat(saved.latitude).isEqualByComparingTo(verified.latitude)
        assertThat(saved.longitude).isEqualByComparingTo(verified.longitude)
        assertThat(result.name).isEqualTo(verified.name)
        assertThat(result.address).isEqualTo(verified.address)
    }

    @Test
    fun `repeated registration returns one internal restaurant without saving a duplicate`() {
        val selected = place(kakaoPlaceId = "idempotent-place")
        val repository = FakeRestaurantRepository()
        val kakaoLocal = FakeKakaoLocalPort(results = listOf(selected))
        val service = RestaurantService(repository, kakaoLocal)
        val command = RegisterRestaurantCommand(
            query = "반복 검색어",
            kakaoPlaceId = selected.kakaoPlaceId,
        )

        val first = service.register(command)
        val second = service.register(command)

        assertThat(second.restaurantId).isEqualTo(first.restaurantId)
        assertThat(repository.saved).hasSize(1)
        assertThat(kakaoLocal.queries).containsExactly("반복 검색어", "반복 검색어")
    }

    @Test
    fun `register returns the concurrent winner after a unique constraint race`() {
        val selected = place(kakaoPlaceId = "racing-place")
        val winner = restaurant(kakaoPlaceId = selected.kakaoPlaceId, name = "동시 등록 승자")
        val repository = RacingRestaurantRepository(winner)
        val service = RestaurantService(
            restaurantRepository = repository,
            kakaoLocalPort = FakeKakaoLocalPort(results = listOf(selected)),
        )

        val result = service.register(
            RegisterRestaurantCommand(
                query = "동시 검색",
                kakaoPlaceId = selected.kakaoPlaceId,
            ),
        )

        assertThat(result.restaurantId).isEqualTo(winner.id)
        assertThat(result.name).isEqualTo(winner.name)
        assertThat(repository.findCount).isEqualTo(2)
    }

    private class FakeKakaoLocalPort(
        private val results: List<PlaceCandidate> = emptyList(),
    ) : KakaoLocalPort {
        val queries = mutableListOf<String>()

        override fun searchByKeyword(query: String): List<PlaceCandidate> {
            queries += query
            return results
        }
    }

    private open class FakeRestaurantRepository(
        private val searchResults: List<Restaurant> = emptyList(),
        protected val restaurants: MutableMap<String, Restaurant> = mutableMapOf(),
    ) : RestaurantRepository {
        val saved = mutableListOf<Restaurant>()

        override fun searchByNameOrAddress(query: String): List<Restaurant> = searchResults

        override fun findById(id: Long): Restaurant? = restaurants.values.find { it.id == id }

        override fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant? = restaurants[kakaoPlaceId]

        override fun save(restaurant: Restaurant): Restaurant {
            if (restaurant.id == 0L) restaurant.id = restaurants.size + 100L
            saved += restaurant
            restaurants[restaurant.kakaoPlaceId] = restaurant
            return restaurant
        }
    }

    private class RacingRestaurantRepository(
        private val winner: Restaurant,
    ) : FakeRestaurantRepository() {
        var findCount = 0

        override fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant? {
            findCount += 1
            return if (findCount == 1) null else winner
        }

        override fun save(restaurant: Restaurant): Restaurant {
            throw DataIntegrityViolationException("duplicate Kakao place id")
        }
    }

    private fun restaurant(
        kakaoPlaceId: String,
        name: String = "테스트 식당",
        address: String = "서울 강남구 테헤란로 1",
        latitude: BigDecimal = BigDecimal("37.4987654"),
        longitude: BigDecimal = BigDecimal("127.0276543"),
    ) = Restaurant(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    ).apply { id = nextRestaurantId++ }

    private fun place(
        kakaoPlaceId: String,
        name: String = "테스트 식당",
        address: String = "서울 강남구 테헤란로 1",
        latitude: BigDecimal = BigDecimal("37.4987654"),
        longitude: BigDecimal = BigDecimal("127.0276543"),
    ) = PlaceCandidate(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )
}
