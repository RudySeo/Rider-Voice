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

    @Test
    fun `active restaurant can be renamed closed and reopened`() {
        val restaurant = Restaurant("기존 브랜드", pickupLocation())

        restaurant.rename("  새 브랜드  ")
        restaurant.close()

        assertThat(restaurant.brandName).isEqualTo("새 브랜드")
        assertThat(restaurant.status).isEqualTo(RestaurantStatus.CLOSED)

        restaurant.reopen()

        assertThat(restaurant.status).isEqualTo(RestaurantStatus.ACTIVE)
    }

    @Test
    fun `closed restaurant rejects active-only mutations and invalid state transitions`() {
        val restaurant = Restaurant("브랜드", pickupLocation())
        restaurant.close()

        assertThatIllegalStateException().isThrownBy { restaurant.close() }
        assertThatIllegalStateException().isThrownBy { restaurant.rename("변경") }
        assertThatIllegalStateException().isThrownBy { restaurant.relinkPickupLocation(pickupLocation("서울 강남구 역삼로 2")) }
        assertThatIllegalStateException().isThrownBy { restaurant.mergeInto(Restaurant("대표", pickupLocation())) }

        restaurant.reopen()
        assertThatIllegalStateException().isThrownBy { restaurant.reopen() }
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
