package com.ridervoice.api.restaurant.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantTest {

    @Test
    fun `restaurant keeps provider place information`() {
        val restaurant = restaurant()

        assertThat(restaurant.kakaoPlaceId).isEqualTo("1234567890")
        assertThat(restaurant.name).isEqualTo("라이더보이스 강남점")
        assertThat(restaurant.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(restaurant.latitude).isEqualByComparingTo("37.4987654")
        assertThat(restaurant.longitude).isEqualByComparingTo("127.0276543")
    }

    @Test
    fun `Kakao place id must not be blank`() {
        listOf("", " ", "\t").forEach { kakaoPlaceId ->
            assertThatIllegalArgumentException()
                .isThrownBy { restaurant(kakaoPlaceId = kakaoPlaceId) }
        }
    }

    @Test
    fun `restaurant name must not be blank`() {
        listOf("", " ", "\t").forEach { name ->
            assertThatIllegalArgumentException()
                .isThrownBy { restaurant(name = name) }
        }
    }

    @Test
    fun `restaurant address must not be blank`() {
        listOf("", " ", "\t").forEach { address ->
            assertThatIllegalArgumentException()
                .isThrownBy { restaurant(address = address) }
        }
    }

    @Test
    fun `latitude must be between minus 90 and 90 inclusive`() {
        assertThat(restaurant(latitude = BigDecimal("-90")).latitude).isEqualByComparingTo("-90")
        assertThat(restaurant(latitude = BigDecimal("90")).latitude).isEqualByComparingTo("90")
        assertThatIllegalArgumentException()
            .isThrownBy { restaurant(latitude = BigDecimal("-90.0000001")) }
        assertThatIllegalArgumentException()
            .isThrownBy { restaurant(latitude = BigDecimal("90.0000001")) }
    }

    @Test
    fun `longitude must be between minus 180 and 180 inclusive`() {
        assertThat(restaurant(longitude = BigDecimal("-180")).longitude).isEqualByComparingTo("-180")
        assertThat(restaurant(longitude = BigDecimal("180")).longitude).isEqualByComparingTo("180")
        assertThatIllegalArgumentException()
            .isThrownBy { restaurant(longitude = BigDecimal("-180.0000001")) }
        assertThatIllegalArgumentException()
            .isThrownBy { restaurant(longitude = BigDecimal("180.0000001")) }
    }

    private fun restaurant(
        kakaoPlaceId: String = "1234567890",
        name: String = "라이더보이스 강남점",
        address: String = "서울 강남구 테헤란로 1",
        latitude: BigDecimal = BigDecimal("37.4987654"),
        longitude: BigDecimal = BigDecimal("127.0276543"),
    ) = Restaurant(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )
}
