package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class RestaurantPersistenceIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var restaurants: RestaurantRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `adapter persists and maps repository lookup operations`() {
        val restaurant = restaurants.save(
            Restaurant(
                kakaoPlaceId = "1234567890",
                name = "라이더보이스 강남점",
                address = "서울 강남구 테헤란로 1",
                latitude = BigDecimal("37.4987654"),
                longitude = BigDecimal("127.0276543"),
            ),
        )

        val foundById = restaurants.findById(restaurant.id)
        val foundByKakaoPlaceId = restaurants.findByKakaoPlaceId("1234567890")

        assertThat(foundById).isSameAs(restaurant)
        assertThat(foundByKakaoPlaceId).isSameAs(restaurant)
        assertThat(foundByKakaoPlaceId?.name).isEqualTo("라이더보이스 강남점")
        assertThat(foundByKakaoPlaceId?.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(foundByKakaoPlaceId?.latitude).isEqualByComparingTo("37.4987654")
        assertThat(foundByKakaoPlaceId?.longitude).isEqualByComparingTo("127.0276543")
        assertThat(restaurants.findById(Long.MAX_VALUE)).isNull()
        assertThat(restaurants.findByKakaoPlaceId("missing-place-id")).isNull()
    }

    @Test
    fun `adapter searches internal restaurants by partial name or address`() {
        val byName = restaurants.save(
            Restaurant(
                kakaoPlaceId = "search-name",
                name = "강남 라이더 분식",
                address = "서울 서초구 서초동 1",
                latitude = BigDecimal("37.5000000"),
                longitude = BigDecimal("127.0000000"),
            ),
        )
        val byAddress = restaurants.save(
            Restaurant(
                kakaoPlaceId = "search-address",
                name = "라이더 김밥",
                address = "서울 강남구 역삼동 123",
                latitude = BigDecimal("37.5000000"),
                longitude = BigDecimal("127.0000000"),
            ),
        )
        restaurants.save(
            Restaurant(
                kakaoPlaceId = "not-matched",
                name = "성수 라이더 카페",
                address = "서울 성동구 성수동 1",
                latitude = BigDecimal("37.5000000"),
                longitude = BigDecimal("127.0000000"),
            ),
        )

        assertThat(restaurants.searchByNameOrAddress("강남"))
            .containsExactlyInAnyOrder(byName, byAddress)
        assertThat(restaurants.searchByNameOrAddress("역삼동"))
            .containsExactly(byAddress)
    }

    @Test
    fun `duplicate Kakao place id is rejected`() {
        restaurants.save(restaurant(kakaoPlaceId = "duplicate-place-id", name = "첫 번째 음식점"))

        assertThatThrownBy {
            restaurants.save(restaurant(kakaoPlaceId = "duplicate-place-id", name = "두 번째 음식점"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `hibernate schema excludes pilot column and preserves Kakao place unique constraint`() {
        val includedInPilotColumnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'restaurants'
              AND column_name = 'included_in_pilot'
            """.trimIndent(),
            Int::class.java,
        )
        val kakaoPlaceUniqueConstraintCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND table_name = 'restaurants'
              AND constraint_name = 'uk_restaurants_kakao_place_id'
              AND constraint_type = 'UNIQUE'
            """.trimIndent(),
            Int::class.java,
        )
        val identityColumnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'restaurants'
              AND column_name = 'id'
              AND data_type = 'bigint'
              AND extra LIKE '%auto_increment%'
            """.trimIndent(),
            Int::class.java,
        )

        assertThat(includedInPilotColumnCount).isZero()
        assertThat(kakaoPlaceUniqueConstraintCount).isOne()
        assertThat(identityColumnCount).isOne()
    }

    private fun restaurant(kakaoPlaceId: String, name: String) = Restaurant(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = "서울 강남구 역삼동 1",
        latitude = BigDecimal("37.5000000"),
        longitude = BigDecimal("127.0000000"),
    )
}
