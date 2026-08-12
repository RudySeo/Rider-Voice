package com.ridervoice.api.restaurant.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantNormalizationTest {

    @Test
    fun `brand names normalize unicode and repeated whitespace without storing a second name`() {
        val location = pickupLocation()

        val first = Restaurant("  Ｒｉｄｅｒ   Voice 강남점  ", location)
        val second = Restaurant("rider voice   강남점", location)

        assertThat(first.brandName).isEqualTo("Rider Voice 강남점")
        assertThat(second.brandName).isEqualTo("rider voice 강남점")
        assertThat(Restaurant::class.java.declaredFields.map { it.name }).doesNotContain("normalizedName")
    }

    @Test
    fun `location key reduces unicode and whitespace differences without inferring address parts`() {
        val first = PickupLocation(
            standardAddress = "  서울특별시  강남구 테헤란로 １  ",
            detailAddress = " 지하  1층 ",
            latitude = BigDecimal("37.5000"),
            longitude = BigDecimal("127.0000"),
            source = PickupLocationSource.MANUAL_ADDRESS,
        )
        val second = PickupLocation(
            standardAddress = "서울특별시 강남구 테헤란로 1",
            detailAddress = "지하 1층",
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127"),
            source = PickupLocationSource.MANUAL_ADDRESS,
        )
        val withoutDetail = PickupLocation(
            standardAddress = "서울특별시 강남구 테헤란로 1",
            detailAddress = null,
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127"),
            source = PickupLocationSource.MANUAL_ADDRESS,
        )

        assertThat(first.standardAddress).isEqualTo("서울특별시 강남구 테헤란로 1")
        assertThat(first.normalizedAddress).isEqualTo("서울특별시 강남구 테헤란로 1")
        assertThat(first.detailAddress).isEqualTo("지하 1층")
        assertThat(first.locationKey).isEqualTo(second.locationKey)
        assertThat(withoutDetail.locationKey).isNotEqualTo(first.locationKey)
        assertThat(withoutDetail.detailAddress).isNull()
    }

    @Test
    fun `blank detail address is treated as absent`() {
        val location = PickupLocation(
            standardAddress = "서울 강남구 테헤란로 1",
            detailAddress = " \t ",
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            source = PickupLocationSource.KAKAO,
        )

        assertThat(location.detailAddress).isNull()
    }

    private fun pickupLocation() = PickupLocation(
        standardAddress = "서울 강남구 테헤란로 1",
        detailAddress = null,
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        source = PickupLocationSource.KAKAO,
    )
}
