package com.ridervoice.api.restaurant.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantDomainTest {

    @Test
    fun `pickup location rejects blank address and coordinates outside earth bounds`() {
        assertThatIllegalArgumentException().isThrownBy {
            pickupLocation(standardAddress = "  ")
        }
        assertThatIllegalArgumentException().isThrownBy {
            pickupLocation(latitude = BigDecimal("90.0001"))
        }
        assertThatIllegalArgumentException().isThrownBy {
            pickupLocation(longitude = BigDecimal("-180.0001"))
        }
    }

    @Test
    fun `restaurant starts active and can merge only into a different active restaurant`() {
        val location = pickupLocation()
        val duplicate = Restaurant("중복 브랜드", location)
        val canonical = Restaurant("대표 브랜드", location)

        assertThat(duplicate.status).isEqualTo(RestaurantStatus.ACTIVE)
        assertThat(duplicate.canonicalRestaurant).isNull()

        duplicate.mergeInto(canonical)

        assertThat(duplicate.status).isEqualTo(RestaurantStatus.MERGED)
        assertThat(duplicate.canonicalRestaurant).isSameAs(canonical)
        assertThatIllegalStateException().isThrownBy { duplicate.mergeInto(canonical) }
        assertThatIllegalArgumentException().isThrownBy { canonical.mergeInto(canonical) }
    }

    @Test
    fun `restaurant cannot merge into an already merged restaurant`() {
        val location = pickupLocation()
        val first = Restaurant("첫 번째", location)
        val second = Restaurant("두 번째", location)
        val canonical = Restaurant("대표", location)
        second.mergeInto(canonical)

        assertThatIllegalArgumentException().isThrownBy { first.mergeInto(second) }
    }

    @Test
    fun `external reference and platform require valid fixed values`() {
        val restaurant = Restaurant("브랜드", pickupLocation())

        val reference = RestaurantExternalReference(
            restaurant = restaurant,
            provider = RestaurantExternalProvider.KAKAO,
            externalPlaceId = " 1234567890 ",
        )
        val platform = RestaurantPlatform(restaurant, DeliveryPlatform.BAEMIN)

        assertThat(reference.externalPlaceId).isEqualTo("1234567890")
        assertThat(platform.platform).isEqualTo(DeliveryPlatform.BAEMIN)
        assertThat(DeliveryPlatform.entries).containsExactly(
            DeliveryPlatform.BAEMIN,
            DeliveryPlatform.COUPANG_EATS,
            DeliveryPlatform.YOGIYO,
            DeliveryPlatform.OTHER,
        )
        assertThatIllegalArgumentException().isThrownBy {
            RestaurantExternalReference(restaurant, RestaurantExternalProvider.KAKAO, "  ")
        }
    }

    @Test
    fun `restaurant rejects a blank brand name`() {
        assertThatIllegalArgumentException().isThrownBy {
            Restaurant("  ", pickupLocation())
        }
    }

    private fun pickupLocation(
        standardAddress: String = "서울 강남구 테헤란로 1",
        latitude: BigDecimal = BigDecimal("37.5"),
        longitude: BigDecimal = BigDecimal("127.0"),
    ) = PickupLocation(
        standardAddress = standardAddress,
        detailAddress = null,
        latitude = latitude,
        longitude = longitude,
        source = PickupLocationSource.KAKAO,
    )
}
