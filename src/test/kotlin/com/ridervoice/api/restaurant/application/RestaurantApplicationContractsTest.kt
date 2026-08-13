package com.ridervoice.api.restaurant.application

import com.ridervoice.api.restaurant.application.model.AddressSearchCandidate
import com.ridervoice.api.restaurant.application.model.AddressSearchResult
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ExternalSearchStatus
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.model.RestaurantCandidateType
import com.ridervoice.api.restaurant.application.model.RestaurantSearchCandidate
import com.ridervoice.api.restaurant.application.model.RestaurantSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.ExistingRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualExistingLocationRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ResolveRestaurantTargetUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ResolvedRestaurantTargetResult
import com.ridervoice.api.restaurant.application.port.`in`.RestaurantTargetType
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchAddressesUseCase
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsCommand
import com.ridervoice.api.restaurant.application.port.`in`.SearchRestaurantsUseCase
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantPlatformRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantApplicationContractsTest {

    @Test
    fun `public search exposes internal and Kakao candidates without provider DTOs`() {
        val result = RestaurantSearchResult(
            externalSearchStatus = ExternalSearchStatus.AVAILABLE,
            candidates = listOf(
                RestaurantSearchCandidate(
                    candidateType = RestaurantCandidateType.INTERNAL,
                    restaurantId = 10L,
                    kakaoPlaceId = "kakao-10",
                    name = "내부 브랜드",
                    address = "서울 강남구 테헤란로 1",
                    aggregationStatus = AggregationStatus.COLLECTING,
                    contributorCount = 3,
                ),
                RestaurantSearchCandidate(
                    candidateType = RestaurantCandidateType.KAKAO,
                    restaurantId = null,
                    kakaoPlaceId = "kakao-20",
                    name = "외부 후보",
                    address = "서울 강남구 역삼로 1",
                    aggregationStatus = AggregationStatus.NO_REVIEWS,
                    contributorCount = 0,
                ),
            ),
        )
        val useCase = SearchRestaurantsUseCase { command ->
            assertThat(command).isEqualTo(SearchRestaurantsCommand("강남 분식"))
            result
        }

        assertThat(useCase.search(SearchRestaurantsCommand("강남 분식"))).isEqualTo(result)
    }

    @Test
    fun `USER address search carries the authenticated user ID and provider neutral locations`() {
        val result = AddressSearchResult(
            query = "서울 강남구 테헤란로 1",
            candidates = listOf(
                AddressSearchCandidate(
                    standardAddress = "서울 강남구 테헤란로 1",
                    lotNumberAddress = "서울 강남구 역삼동 1",
                    latitude = BigDecimal("37.12345678"),
                    longitude = BigDecimal("127.12345678"),
                    existingPickupLocationId = 20L,
                ),
            ),
        )
        val useCase = SearchAddressesUseCase { command ->
            assertThat(command.userId).isEqualTo(7L)
            result
        }

        assertThat(
            useCase.search(SearchAddressesCommand(7L, "서울 강남구 테헤란로 1")),
        ).isEqualTo(result)
    }

    @Test
    fun `target resolution supports all four target commands and returns only restaurant identity`() {
        val commands = listOf(
            ExistingRestaurantTargetCommand(10L),
            KakaoRestaurantTargetCommand("강남 분식", "kakao-10"),
            ManualExistingLocationRestaurantTargetCommand(
                pickupLocationId = 20L,
                name = "새 배달 브랜드",
                platforms = setOf(DeliveryPlatform.BAEMIN),
            ),
            ManualAddressRestaurantTargetCommand(
                addressQuery = "서울 강남구 테헤란로 1",
                selectedStandardAddress = "서울 강남구 테헤란로 1",
                detailAddress = "지하 1층 픽업대",
                name = "새 배달 브랜드",
                platforms = setOf(DeliveryPlatform.COUPANG_EATS),
            ),
        )
        val useCase = ResolveRestaurantTargetUseCase { ResolvedRestaurantTargetResult(99L) }

        assertThat(commands.map { it.type }).containsExactly(
            RestaurantTargetType.EXISTING,
            RestaurantTargetType.KAKAO,
            RestaurantTargetType.MANUAL_EXISTING_LOCATION,
            RestaurantTargetType.MANUAL_ADDRESS,
        )
        commands.forEach { command ->
            assertThat(useCase.resolve(command)).isEqualTo(ResolvedRestaurantTargetResult(99L))
        }
        assertThat(ResolvedRestaurantTargetResult::class.java.declaredFields.map { it.name })
            .containsExactly("restaurantId")
    }

    @Test
    fun `Kakao output ports expose application candidates and provider neutral failures`() {
        val keywordCandidate = ExternalRestaurantCandidate(
            kakaoPlaceId = "kakao-10",
            name = "후보 브랜드",
            standardAddress = "서울 강남구 테헤란로 1",
            lotNumberAddress = null,
            latitude = BigDecimal("37.12345678"),
            longitude = BigDecimal("127.12345678"),
        )
        val addressCandidate = ExternalAddressCandidate(
            standardAddress = keywordCandidate.standardAddress,
            lotNumberAddress = "서울 강남구 역삼동 1",
            latitude = keywordCandidate.latitude,
            longitude = keywordCandidate.longitude,
        )
        val keywordPort = KakaoKeywordSearchPort { query, limit ->
            assertThat(query).isEqualTo("강남 분식")
            assertThat(limit).isEqualTo(20)
            ProviderSearchResult.Available(listOf(keywordCandidate))
        }
        val addressPort = KakaoAddressSearchPort { _, _ ->
            ProviderSearchResult.Available(listOf(addressCandidate))
        }

        assertThat(keywordPort.search("강남 분식", 20))
            .isEqualTo(ProviderSearchResult.Available(listOf(keywordCandidate)))
        assertThat(addressPort.search("주소", 20))
            .isEqualTo(ProviderSearchResult.Available(listOf(addressCandidate)))
        assertThat(ProviderSearchResult.Unavailable(ProviderFailureReason.TIMEOUT).reason)
            .isEqualTo(ProviderFailureReason.TIMEOUT)
    }

    @Test
    fun `persistence dependencies are repository output ports`() {
        assertThat(PickupLocationRepository::class.java.isInterface).isTrue()
        assertThat(RestaurantRepository::class.java.isInterface).isTrue()
        assertThat(RestaurantPlatformRepository::class.java.isInterface).isTrue()
    }
}
