package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class RestaurantPersistenceIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var restaurants: RestaurantRepository

    @Test
    fun `restaurant is persisted and found by Kakao place id`() {
        val restaurant = restaurants.saveAndFlush(
            Restaurant(
                kakaoPlaceId = "1234567890",
                name = "라이더보이스 강남점",
                address = "서울 강남구 테헤란로 1",
                latitude = BigDecimal("37.4987654"),
                longitude = BigDecimal("127.0276543"),
                includedInPilot = true,
            ),
        )

        val found = restaurants.findByKakaoPlaceId("1234567890")

        assertThat(found).contains(restaurant)
        assertThat(found.orElseThrow().name).isEqualTo("라이더보이스 강남점")
        assertThat(found.orElseThrow().address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(found.orElseThrow().latitude).isEqualByComparingTo("37.4987654")
        assertThat(found.orElseThrow().longitude).isEqualByComparingTo("127.0276543")
        assertThat(found.orElseThrow().includedInPilot).isTrue()
    }

    @Test
    fun `duplicate Kakao place id is rejected`() {
        restaurants.saveAndFlush(restaurant(kakaoPlaceId = "duplicate-place-id", name = "첫 번째 음식점"))

        assertThatThrownBy {
            restaurants.saveAndFlush(restaurant(kakaoPlaceId = "duplicate-place-id", name = "두 번째 음식점"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun restaurant(kakaoPlaceId: String, name: String) = Restaurant(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = "서울 강남구 역삼동 1",
        latitude = BigDecimal("37.5000000"),
        longitude = BigDecimal("127.0000000"),
        includedInPilot = false,
    )
}
