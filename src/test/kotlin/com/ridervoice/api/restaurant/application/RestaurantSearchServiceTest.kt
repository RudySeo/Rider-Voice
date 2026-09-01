package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.BadRequestException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandSummary
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
import com.ridervoice.api.restaurant.application.model.StoredLinkedRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.StoredRestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.PublicKakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantPlatformRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantSearchLinkQuery
import com.ridervoice.api.restaurant.application.port.out.RestaurantSearchReviewSummaryProvider
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal

class RestaurantSearchServiceTest {

    @Test
    fun `search merges internal and Kakao candidates by Kakao place ID`() {
        val first = stored(1L, null, "내부 브랜드", "서울 강남구 1")
        val firstRestaurant = restaurant(1L, "내부 브랜드", "서울 강남구 1", "kakao-1")
        val linkedRestaurant = restaurant(2L, "연결 브랜드", "서울 강남구 2", "kakao-2")
        val repositories = InMemoryRepositories(
            searchResults = listOf(first),
            restaurants = mutableListOf(firstRestaurant, linkedRestaurant),
        )
        val keywordSearch = PublicKakaoKeywordSearchPort { query, limit ->
            assertThat(query).isEqualTo("강남 분식")
            assertThat(limit).isEqualTo(20)
            ProviderSearchResult.Available(
                listOf(
                    externalRestaurant("kakao-1", "중복 후보", "서울 강남구 1"),
                    externalRestaurant("kakao-2", "카카오 표기", "서울 강남구 2"),
                    externalRestaurant("kakao-3", "신규 후보", "서울 강남구 3"),
                ),
            )
        }
        val summaries = RecordingReviewSummaryProvider(
            mapOf(
                1L to RestaurantBrandSummary(AggregationStatus.COLLECTING, 3),
                2L to RestaurantBrandSummary(AggregationStatus.PUBLISHED, 5),
            ),
        )
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            keywordSearch,
            addressSearch(),
            repositories,
            summaries,
        )

        val result = service.search(SearchRestaurantsCommand("  강남   분식  "))

        assertThat(result.externalSearchStatus).isEqualTo(ExternalSearchStatus.AVAILABLE)
        assertThat(result.candidates.map { it.candidateType }).containsExactly(
            RestaurantCandidateType.INTERNAL,
            RestaurantCandidateType.INTERNAL,
            RestaurantCandidateType.KAKAO,
        )
        assertThat(result.candidates.map { it.restaurantId }).containsExactly(1L, 2L, null)
        assertThat(result.candidates.map { it.kakaoPlaceId })
            .containsExactly("kakao-1", "kakao-2", "kakao-3")
        assertThat(result.candidates.map { it.aggregationStatus }).containsExactly(
            AggregationStatus.COLLECTING,
            AggregationStatus.PUBLISHED,
            AggregationStatus.NO_REVIEWS,
        )
        assertThat(result.candidates.map { it.contributorCount }).containsExactly(3, 5, 0)
        assertThat(repositories.requestedKakaoPlaceIds).containsExactly(
            setOf("kakao-1", "kakao-2", "kakao-3"),
        )
        assertThat(summaries.requestedRestaurantIds).containsExactly(setOf(1L, 2L))
    }

    @Test
    fun `search returns internal candidates and UNAVAILABLE when Kakao fails`() {
        val repositories = InMemoryRepositories(searchResults = listOf(stored(1L, null, "내부", "서울 1")))
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            PublicKakaoKeywordSearchPort { _, _ ->
                ProviderSearchResult.Unavailable(ProviderFailureReason.TIMEOUT)
            },
            addressSearch(),
            repositories,
            RecordingReviewSummaryProvider(
                mapOf(1L to RestaurantBrandSummary(AggregationStatus.COLLECTING, 4)),
            ),
        )

        val result = service.search(SearchRestaurantsCommand("내부"))

        assertThat(result.externalSearchStatus).isEqualTo(ExternalSearchStatus.UNAVAILABLE)
        val candidate = result.candidates.single()
        assertThat(candidate.candidateType).isEqualTo(RestaurantCandidateType.INTERNAL)
        assertThat(candidate.restaurantId).isEqualTo(1L)
        assertThat(candidate.aggregationStatus).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(candidate.contributorCount).isEqualTo(4)
    }

    @Test
    fun `search suppresses a Kakao candidate already linked to a closed restaurant`() {
        val closed = restaurant(2L, "폐업 브랜드", "서울 강남구 2", "closed-place")
            .also { it.close() }
        val repositories = InMemoryRepositories(
            restaurants = mutableListOf(closed),
        )
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            PublicKakaoKeywordSearchPort { _, _ ->
                ProviderSearchResult.Available(
                    listOf(externalRestaurant("closed-place", "폐업 브랜드", "서울 강남구 2")),
                )
            },
            addressSearch(),
            repositories,
            RecordingReviewSummaryProvider(),
        )

        assertThat(service.search(SearchRestaurantsCommand("폐업 브랜드")).candidates).isEmpty()
    }

    @Test
    fun `address search uses provider results and marks an exact existing pickup location`() {
        val existing = pickup(10L, "서울 강남구 테헤란로 1", null)
        val repositories = InMemoryRepositories(pickupLocations = mutableListOf(existing))
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            publicKeywordSearch(),
            addressSearch(
                ProviderSearchResult.Available(
                    listOf(externalAddress("서울 강남구 테헤란로 1")),
                ),
            ),
            repositories,
            RecordingReviewSummaryProvider(),
        )

        val result = service.search(SearchAddressesCommand(7L, "  서울 강남구  테헤란로 1 "))

        assertThat(result.query).isEqualTo("서울 강남구 테헤란로 1")
        val candidate = result.candidates.single()
        assertThat(candidate.standardAddress).isEqualTo(existing.standardAddress)
        assertThat(candidate.existingPickupLocationId).isEqualTo(10L)
    }

    @Test
    fun `existing target resolves an active restaurant by its own ID`() {
        val restaurant = restaurant(1L, "브랜드", "서울 1")
        val repositories = InMemoryRepositories(restaurants = mutableListOf(restaurant))
        val service = targetService(repositories)

        assertThat(service.resolve(ExistingRestaurantTargetCommand(1L)).restaurantId).isEqualTo(1L)
        assertThatThrownBy { service.resolve(ExistingRestaurantTargetCommand(99L)) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `existing target rejects a closed restaurant for a new review`() {
        val closed = restaurant(3L, "폐업 브랜드", "서울 3").also { it.close() }
        val service = targetService(InMemoryRepositories(restaurants = mutableListOf(closed)))

        assertThatThrownBy { service.resolve(ExistingRestaurantTargetCommand(3L)) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `Kakao target repeats keyword search and persists only the selected provider candidate`() {
        val repositories = InMemoryRepositories()
        var searchedQuery: String? = null
        val service = targetService(
            repositories,
            keywordSearch = KakaoKeywordSearchPort { query, _ ->
                searchedQuery = query
                ProviderSearchResult.Available(
                    listOf(externalRestaurant("selected", "검증 브랜드", "검증 주소")),
                )
            },
        )

        val result = service.resolve(KakaoRestaurantTargetCommand("  검증   검색 ", "selected"))

        assertThat(searchedQuery).isEqualTo("검증 검색")
        assertThat(result.restaurantId).isPositive()
        assertThat(repositories.pickupLocations.single().standardAddress).isEqualTo("검증 주소")
        assertThat(repositories.restaurants.single().brandName).isEqualTo("검증 브랜드")
        assertThat(repositories.restaurants.single().kakaoPlaceId).isEqualTo("selected")
    }

    @Test
    fun `Kakao target rejects a place ID absent from the repeated search`() {
        val repositories = InMemoryRepositories()
        val service = targetService(repositories)

        assertThatThrownBy {
            service.resolve(KakaoRestaurantTargetCommand("검색어", "tampered"))
        }.isInstanceOf(BadRequestException::class.java)
        assertThat(repositories.restaurants).isEmpty()
    }

    @Test
    fun `Kakao target rejects overwriting a different place ID on the same restaurant`() {
        val location = pickup(10L, "검증 주소", null)
        val existing = restaurant(20L, "검증 브랜드", location, "existing-place")
        val repositories = InMemoryRepositories(
            pickupLocations = mutableListOf(location),
            restaurants = mutableListOf(existing),
        )
        val service = targetService(
            repositories,
            keywordSearch = keywordSearch(
                ProviderSearchResult.Available(
                    listOf(externalRestaurant("different-place", "검증 브랜드", "검증 주소")),
                ),
            ),
        )

        assertThatThrownBy {
            service.resolve(KakaoRestaurantTargetCommand("검증 브랜드", "different-place"))
        }.isInstanceOf(StateConflictException::class.java)
        assertThat(existing.kakaoPlaceId).isEqualTo("existing-place")
    }

    @Test
    fun `manual address target repeats address search and stores provider address and coordinates`() {
        val repositories = InMemoryRepositories()
        val verified = externalAddress("서울 강남구 검증로 1")
        val service = targetService(
            repositories,
            addressSearch = addressSearch(ProviderSearchResult.Available(listOf(verified))),
        )

        val result = service.resolve(
            ManualAddressRestaurantTargetCommand(
                addressQuery = "검증로 1",
                selectedStandardAddress = "  서울 강남구  검증로 1 ",
                detailAddress = " 지하 1층 ",
                name = " 수동 브랜드 ",
                platforms = setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )

        assertThat(result.restaurantId).isPositive()
        val location = repositories.pickupLocations.single()
        assertThat(location.standardAddress).isEqualTo(verified.standardAddress)
        assertThat(location.latitude).isEqualByComparingTo(verified.latitude)
        assertThat(location.longitude).isEqualByComparingTo(verified.longitude)
        assertThat(location.detailAddress).isEqualTo("지하 1층")
        assertThat(repositories.platforms.map { it.platform })
            .containsExactly(DeliveryPlatform.COUPANG_EATS)
    }

    @Test
    fun `manual existing location creates or reads a normalized brand`() {
        val location = pickup(10L, "서울 강남구 1", null)
        val repositories = InMemoryRepositories(pickupLocations = mutableListOf(location))
        val service = targetService(repositories)

        val first = service.resolve(
            ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = 10L,
                name = " Ｎｅｗ   Brand ",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
        )
        val second = service.resolve(
            ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = 10L,
                name = "new brand",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
        )

        assertThat(second).isEqualTo(first)
        assertThat(repositories.restaurants).hasSize(1)
        assertThat(repositories.platforms).hasSize(1)
    }

    @Test
    fun `unique collision retries the transaction and reads the winning restaurant`() {
        val location = pickup(10L, "서울 강남구 1", null)
        val winner = restaurant(30L, "경합 브랜드", location)
        val repositories = InMemoryRepositories(pickupLocations = mutableListOf(location))
        repositories.restaurantSaveFailure = {
            repositories.restaurants += winner
            DataIntegrityViolationException("concurrent winner")
        }
        val service = targetService(repositories)

        val result = service.resolve(
            ManualExistingLocationRestaurantTargetCommand(10L, "경합 브랜드", emptySet()),
        )

        assertThat(result.restaurantId).isEqualTo(30L)
        assertThat(repositories.restaurantSaveAttempts).isEqualTo(1)
    }

    private fun targetService(
        repositories: InMemoryRepositories,
        keywordSearch: KakaoKeywordSearchPort = keywordSearch(),
        addressSearch: KakaoAddressSearchPort = addressSearch(),
    ): RestaurantTargetResolutionService = RestaurantTargetResolutionService(
        keywordSearch = keywordSearch,
        addressSearch = addressSearch,
        targetWriter = TransactionalRestaurantTargetWriter(
            pickupLocations = PickupRepository(repositories),
            restaurants = repositories,
            platforms = repositories,
        ),
    )

    private fun keywordSearch(
        result: ProviderSearchResult<ExternalRestaurantCandidate> = ProviderSearchResult.Available(emptyList()),
    ) = KakaoKeywordSearchPort { _, _ -> result }

    private fun publicKeywordSearch(
        result: ProviderSearchResult<ExternalRestaurantCandidate> = ProviderSearchResult.Available(emptyList()),
    ) = PublicKakaoKeywordSearchPort { _, _ -> result }

    private fun addressSearch(
        result: ProviderSearchResult<ExternalAddressCandidate> = ProviderSearchResult.Available(emptyList()),
    ) = KakaoAddressSearchPort { _, _ -> result }

    private fun externalRestaurant(id: String, name: String, address: String) = ExternalRestaurantCandidate(
        kakaoPlaceId = id,
        name = name,
        standardAddress = address,
        lotNumberAddress = null,
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
    )

    private fun externalAddress(address: String) = ExternalAddressCandidate(
        standardAddress = address,
        lotNumberAddress = "서울 강남구 지번 1",
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
    )

    private fun pickup(id: Long, address: String, detail: String?) = PickupLocation(
        standardAddress = address,
        detailAddress = detail,
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
        source = PickupLocationSource.MANUAL_ADDRESS,
    ).also { it.id = id }

    private fun restaurant(id: Long, name: String, address: String, kakaoPlaceId: String? = null) =
        restaurant(id, name, pickup(id * 10, address, null), kakaoPlaceId)

    private fun restaurant(
        id: Long,
        name: String,
        location: PickupLocation,
        kakaoPlaceId: String? = null,
    ) = Restaurant(name, location, kakaoPlaceId).also { it.id = id }

    private fun stored(id: Long, kakaoPlaceId: String?, name: String, address: String) =
        StoredRestaurantSearchCandidate(id, kakaoPlaceId, name, address)

    private class RecordingReviewSummaryProvider(
        private val summaries: Map<Long, RestaurantBrandSummary> = emptyMap(),
    ) : RestaurantSearchReviewSummaryProvider {
        val requestedRestaurantIds = mutableListOf<Set<Long>>()

        override fun findByRestaurantIds(restaurantIds: Set<Long>): Map<Long, RestaurantBrandSummary> {
            requestedRestaurantIds += restaurantIds
            return summaries.filterKeys { it in restaurantIds }
        }
    }

    private class InMemoryRepositories(
        private val searchResults: List<StoredRestaurantSearchCandidate> = emptyList(),
        val pickupLocations: MutableList<PickupLocation> = mutableListOf(),
        val restaurants: MutableList<Restaurant> = mutableListOf(),
        val platforms: MutableList<RestaurantPlatform> = mutableListOf(),
    ) : RestaurantRepository,
        RestaurantSearchLinkQuery,
        RestaurantPlatformRepository {

        var restaurantSaveAttempts: Int = 0
        var restaurantSaveFailure: (() -> DataIntegrityViolationException)? = null
        val requestedKakaoPlaceIds = mutableListOf<Set<String>>()
        private var nextId = 100L

        fun findPickupById(pickupLocationId: Long): PickupLocation? =
            pickupLocations.find { it.id == pickupLocationId }

        fun findPickupByLocationKey(locationKey: String): PickupLocation? =
            pickupLocations.find { it.locationKey == locationKey }

        fun savePickup(pickupLocation: PickupLocation): PickupLocation = pickupLocation.also {
            if (it.id == 0L) it.id = nextId++
            pickupLocations += it
        }

        override fun searchActive(query: String, limit: Int): List<StoredRestaurantSearchCandidate> =
            searchResults.take(limit)

        override fun findByKakaoPlaceIds(
            kakaoPlaceIds: Set<String>,
        ): Map<String, StoredLinkedRestaurantSearchCandidate> {
            requestedKakaoPlaceIds += kakaoPlaceIds
            return restaurants
                .filter { it.kakaoPlaceId in kakaoPlaceIds }
                .associate { restaurant ->
                    restaurant.kakaoPlaceId!! to StoredLinkedRestaurantSearchCandidate(
                        restaurantId = restaurant.id,
                        kakaoPlaceId = restaurant.kakaoPlaceId!!,
                        name = restaurant.brandName,
                        address = restaurant.pickupLocation.standardAddress,
                        status = restaurant.status,
                    )
                }
        }

        override fun findById(restaurantId: Long): Restaurant? = restaurants.find { it.id == restaurantId }

        override fun findActiveById(restaurantId: Long): Restaurant? =
            findById(restaurantId)?.takeIf {
                it.status == com.ridervoice.api.restaurant.domain.RestaurantStatus.ACTIVE
            }

        override fun findSearchCandidateById(restaurantId: Long): StoredRestaurantSearchCandidate? =
            findActiveById(restaurantId)?.let { restaurant ->
                StoredRestaurantSearchCandidate(
                    restaurant.id,
                    restaurant.kakaoPlaceId,
                    restaurant.brandName,
                    restaurant.pickupLocation.standardAddress,
                )
            }

        override fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant? =
            restaurants.find { it.kakaoPlaceId == kakaoPlaceId }

        override fun findByPickupLocationIdAndBrandName(
            pickupLocationId: Long,
            brandName: String,
        ): Restaurant? = restaurants.find {
            it.pickupLocation.id == pickupLocationId &&
                RestaurantNormalization.normalizedText(it.brandName) ==
                RestaurantNormalization.normalizedText(brandName)
        }

        override fun save(restaurant: Restaurant): Restaurant {
            restaurantSaveAttempts++
            restaurantSaveFailure?.also {
                restaurantSaveFailure = null
                throw it()
            }
            if (restaurant.id == 0L) restaurant.id = nextId++
            if (restaurants.none { it.id == restaurant.id }) restaurants += restaurant
            return restaurant
        }

        override fun findPlatforms(restaurantId: Long): Set<DeliveryPlatform> = platforms
            .filter { it.restaurant.id == restaurantId }
            .mapTo(linkedSetOf()) { it.platform }

        override fun saveAll(platforms: Collection<RestaurantPlatform>): List<RestaurantPlatform> =
            platforms.map { platform ->
                if (platform.id == 0L) platform.id = nextId++
                this.platforms += platform
                platform
            }
    }

    private class PickupRepository(
        private val store: InMemoryRepositories,
    ) : PickupLocationRepository {
        override fun findById(pickupLocationId: Long): PickupLocation? =
            store.findPickupById(pickupLocationId)

        override fun findByLocationKey(locationKey: String): PickupLocation? =
            store.findPickupByLocationKey(locationKey)

        override fun save(pickupLocation: PickupLocation): PickupLocation =
            store.savePickup(pickupLocation)
    }
}
