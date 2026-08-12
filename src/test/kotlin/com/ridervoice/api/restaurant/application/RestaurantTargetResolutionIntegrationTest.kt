package com.ridervoice.api.restaurant.application

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.`in`.KakaoRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.`in`.ManualAddressRestaurantTargetCommand
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import com.ridervoice.api.restaurant.application.port.out.RestaurantExternalReferenceRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.DeliveryPlatform
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.RestaurantExternalProvider
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
@Tag("integration")
class RestaurantTargetResolutionIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var restaurants: RestaurantRepository

    @Autowired
    private lateinit var externalReferences: RestaurantExternalReferenceRepository

    @Autowired
    private lateinit var targetWriter: RestaurantTargetWriter

    @Test
    fun `one pickup location supports multiple brands and one brand supports multiple locations`() {
        val firstAddress = address("서울 강남구 통합로 101", "37.50100000", "127.00100000")
        val secondAddress = address("서울 강남구 통합로 202", "37.50200000", "127.00200000")
        val resolver = resolver(
            addresses = mapOf(
                "첫 주소" to firstAddress,
                "둘째 주소" to secondAddress,
            ),
        )

        val firstBrand = resolver.resolve(manualCommand("첫 주소", firstAddress, "공유 장소 첫 브랜드"))
        val secondBrand = resolver.resolve(manualCommand("첫 주소", firstAddress, "공유 장소 둘째 브랜드"))
        val sameBrandElsewhere = resolver.resolve(manualCommand("둘째 주소", secondAddress, "공유 장소 첫 브랜드"))

        val first = restaurants.findById(firstBrand.restaurantId)!!
        val second = restaurants.findById(secondBrand.restaurantId)!!
        val elsewhere = restaurants.findById(sameBrandElsewhere.restaurantId)!!

        assertThat(setOf(first.id, second.id, elsewhere.id)).hasSize(3)
        assertThat(second.pickupLocation.id).isEqualTo(first.pickupLocation.id)
        assertThat(elsewhere.pickupLocation.id).isNotEqualTo(first.pickupLocation.id)
        assertThat(elsewhere.brandName).isEqualTo(first.brandName)
    }

    @Test
    fun `Kakao resolution attaches its reference to a matching manually registered brand`() {
        val selectedAddress = address("서울 강남구 연결로 303", "37.50300000", "127.00300000")
        val place = ExternalRestaurantCandidate(
            externalPlaceId = "integration-kakao-place-303",
            name = "수동 연결 브랜드",
            standardAddress = selectedAddress.standardAddress,
            lotNumberAddress = selectedAddress.lotNumberAddress,
            latitude = selectedAddress.latitude,
            longitude = selectedAddress.longitude,
        )
        val resolver = resolver(
            addresses = mapOf("수동 주소" to selectedAddress),
            places = mapOf("카카오 브랜드" to place),
        )

        val manual = resolver.resolve(manualCommand("수동 주소", selectedAddress, place.name))
        val kakao = resolver.resolve(KakaoRestaurantTargetCommand("카카오 브랜드", place.externalPlaceId))

        val linkedReference = externalReferences.findByProviderAndExternalPlaceId(
            RestaurantExternalProvider.KAKAO,
            place.externalPlaceId,
        )
        val restaurant = restaurants.findById(manual.restaurantId)!!

        assertThat(kakao.restaurantId).isEqualTo(manual.restaurantId)
        assertThat(linkedReference?.restaurant?.id).isEqualTo(manual.restaurantId)
        assertThat(restaurant.pickupLocation.source).isEqualTo(PickupLocationSource.MANUAL_ADDRESS)
    }

    private fun resolver(
        addresses: Map<String, ExternalAddressCandidate> = emptyMap(),
        places: Map<String, ExternalRestaurantCandidate> = emptyMap(),
    ) = RestaurantTargetResolutionService(
        keywordSearch = KakaoKeywordSearchPort { query, _ ->
            ProviderSearchResult.Available(places[query]?.let(::listOf).orEmpty())
        },
        addressSearch = KakaoAddressSearchPort { query, _ ->
            ProviderSearchResult.Available(addresses[query]?.let(::listOf).orEmpty())
        },
        targetWriter = targetWriter,
    )

    private fun manualCommand(
        query: String,
        selectedAddress: ExternalAddressCandidate,
        name: String,
    ) = ManualAddressRestaurantTargetCommand(
        addressQuery = query,
        selectedStandardAddress = selectedAddress.standardAddress,
        detailAddress = null,
        name = name,
        platforms = setOf(DeliveryPlatform.BAEMIN),
    )

    private fun address(
        standardAddress: String,
        latitude: String,
        longitude: String,
    ) = ExternalAddressCandidate(
        standardAddress = standardAddress,
        lotNumberAddress = null,
        latitude = BigDecimal(latitude),
        longitude = BigDecimal(longitude),
    )
}
