package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.BadRequestException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
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
import com.ridervoice.api.restaurant.application.port.out.RestaurantExternalReferenceRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantPlatformRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal

class RestaurantSearchServiceTest {

    @Test
    fun `search merges internal and Kakao candidates by external reference ID`() {
        val first = stored(1L, null, "내부 브랜드", "서울 강남구 1")
        val firstRestaurant = restaurant(1L, "내부 브랜드", "서울 강남구 1")
        val linkedRestaurant = restaurant(2L, "연결 브랜드", "서울 강남구 2")
        val repositories = InMemoryRepositories(
            searchResults = listOf(first),
            restaurants = mutableListOf(firstRestaurant, linkedRestaurant),
            references = mutableListOf(
                reference(10L, firstRestaurant, "kakao-1"),
                reference(20L, linkedRestaurant, "kakao-2"),
            ),
        )
        val keywordSearch = KakaoKeywordSearchPort { query, limit ->
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
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            repositories,
            keywordSearch,
            addressSearch(),
        )

        val result = service.search(SearchRestaurantsCommand("  강남   분식  "))

        assertThat(result.externalSearchStatus).isEqualTo(ExternalSearchStatus.AVAILABLE)
        assertThat(result.candidates.map { it.candidateType }).containsExactly(
            RestaurantCandidateType.INTERNAL,
            RestaurantCandidateType.INTERNAL,
            RestaurantCandidateType.KAKAO,
        )
        assertThat(result.candidates.map { it.restaurantId }).containsExactly(1L, 2L, null)
        assertThat(result.candidates.map { it.externalPlaceId })
            .containsExactly("kakao-1", "kakao-2", "kakao-3")
        assertThat(result.candidates).allSatisfy { candidate ->
            assertThat(candidate.aggregationStatus.name).isEqualTo("NO_REVIEWS")
            assertThat(candidate.contributorCount).isZero()
        }
    }

    @Test
    fun `search returns internal candidates and UNAVAILABLE when Kakao fails`() {
        val repositories = InMemoryRepositories(searchResults = listOf(stored(1L, null, "내부", "서울 1")))
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            repositories,
            KakaoKeywordSearchPort { _, _ ->
                ProviderSearchResult.Unavailable(ProviderFailureReason.TIMEOUT)
            },
            addressSearch(),
        )

        val result = service.search(SearchRestaurantsCommand("내부"))

        assertThat(result.externalSearchStatus).isEqualTo(ExternalSearchStatus.UNAVAILABLE)
        val candidate = result.candidates.single()
        assertThat(candidate.candidateType).isEqualTo(RestaurantCandidateType.INTERNAL)
        assertThat(candidate.restaurantId).isEqualTo(1L)
    }

    @Test
    fun `address search uses provider results and marks an exact existing pickup location`() {
        val existing = pickup(10L, "서울 강남구 테헤란로 1", null)
        val repositories = InMemoryRepositories(pickupLocations = mutableListOf(existing))
        val service = RestaurantSearchService(
            repositories,
            PickupRepository(repositories),
            repositories,
            keywordSearch(),
            addressSearch(
                ProviderSearchResult.Available(
                    listOf(externalAddress("서울 강남구 테헤란로 1")),
                ),
            ),
        )

        val result = service.search(SearchAddressesCommand(7L, "  서울 강남구  테헤란로 1 "))

        assertThat(result.query).isEqualTo("서울 강남구 테헤란로 1")
        val candidate = result.candidates.single()
        assertThat(candidate.standardAddress).isEqualTo(existing.standardAddress)
        assertThat(candidate.existingPickupLocationId).isEqualTo(10L)
    }

    @Test
    fun `existing target resolves a MERGED restaurant to its canonical ID`() {
        val canonical = restaurant(2L, "대표 브랜드", "서울 2")
        val duplicate = restaurant(1L, "중복 브랜드", "서울 1").also { it.mergeInto(canonical) }
        val repositories = InMemoryRepositories(restaurants = mutableListOf(duplicate, canonical))
        val service = targetService(repositories)

        assertThat(service.resolve(ExistingRestaurantTargetCommand(1L)).restaurantId).isEqualTo(2L)
        assertThatThrownBy { service.resolve(ExistingRestaurantTargetCommand(99L)) }
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
        assertThat(repositories.references.single().externalPlaceId).isEqualTo("selected")
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
            externalReferences = repositories,
            platforms = repositories,
        ),
    )

    private fun keywordSearch(
        result: ProviderSearchResult<ExternalRestaurantCandidate> = ProviderSearchResult.Available(emptyList()),
    ) = KakaoKeywordSearchPort { _, _ -> result }

    private fun addressSearch(
        result: ProviderSearchResult<ExternalAddressCandidate> = ProviderSearchResult.Available(emptyList()),
    ) = KakaoAddressSearchPort { _, _ -> result }

    private fun externalRestaurant(id: String, name: String, address: String) = ExternalRestaurantCandidate(
        externalPlaceId = id,
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

    private fun restaurant(id: Long, name: String, address: String) =
        restaurant(id, name, pickup(id * 10, address, null))

    private fun restaurant(id: Long, name: String, location: PickupLocation) =
        Restaurant(name, location).also { it.id = id }

    private fun reference(id: Long, restaurant: Restaurant, externalPlaceId: String) =
        RestaurantExternalReference(restaurant, RestaurantExternalProvider.KAKAO, externalPlaceId)
            .also { it.id = id }

    private fun stored(id: Long, externalPlaceId: String?, name: String, address: String) =
        StoredRestaurantSearchCandidate(id, externalPlaceId, name, address)

    private class InMemoryRepositories(
        private val searchResults: List<StoredRestaurantSearchCandidate> = emptyList(),
        val pickupLocations: MutableList<PickupLocation> = mutableListOf(),
        val restaurants: MutableList<Restaurant> = mutableListOf(),
        val references: MutableList<RestaurantExternalReference> = mutableListOf(),
        val platforms: MutableList<RestaurantPlatform> = mutableListOf(),
    ) : RestaurantRepository,
        RestaurantExternalReferenceRepository,
        RestaurantPlatformRepository {

        var restaurantSaveAttempts: Int = 0
        var restaurantSaveFailure: (() -> DataIntegrityViolationException)? = null
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

        override fun findById(restaurantId: Long): Restaurant? = restaurants.find { it.id == restaurantId }

        override fun findCanonicalById(restaurantId: Long): Restaurant? {
            var current = findById(restaurantId) ?: return null
            val visited = mutableSetOf<Long>()
            while (visited.add(current.id)) {
                current = current.canonicalRestaurant ?: return current
            }
            return null
        }

        override fun findSearchCandidateById(restaurantId: Long): StoredRestaurantSearchCandidate? =
            findCanonicalById(restaurantId)?.let { restaurant ->
                StoredRestaurantSearchCandidate(
                    restaurant.id,
                    null,
                    restaurant.brandName,
                    restaurant.pickupLocation.standardAddress,
                )
            }

        override fun findByPickupLocationIdAndNormalizedName(
            pickupLocationId: Long,
            normalizedName: String,
        ): Restaurant? = restaurants.find {
            it.pickupLocation.id == pickupLocationId && it.normalizedName == normalizedName
        }

        override fun save(restaurant: Restaurant): Restaurant {
            restaurantSaveAttempts++
            restaurantSaveFailure?.also {
                restaurantSaveFailure = null
                throw it()
            }
            if (restaurant.id == 0L) restaurant.id = nextId++
            restaurants += restaurant
            return restaurant
        }

        override fun findByProviderAndExternalPlaceId(
            provider: RestaurantExternalProvider,
            externalPlaceId: String,
        ): RestaurantExternalReference? = references.find {
            it.provider == provider && it.externalPlaceId == externalPlaceId
        }

        override fun save(reference: RestaurantExternalReference): RestaurantExternalReference = reference.also {
            if (it.id == 0L) it.id = nextId++
            references += it
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
