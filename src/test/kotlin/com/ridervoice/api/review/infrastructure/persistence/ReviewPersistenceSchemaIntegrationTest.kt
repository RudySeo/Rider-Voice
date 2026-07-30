package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@Transactional
@Tag("integration")
class ReviewPersistenceSchemaIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var users: UserStore
    @Autowired private lateinit var pickupLocations: PickupLocationRepository
    @Autowired private lateinit var restaurants: RestaurantRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var entityManager: EntityManager

    @Test
    fun `reviews retain inactive history and own one active slot per author restaurant`() {
        val user = users.saveUser(User())
        val location = pickupLocations.save(
            PickupLocation(
                standardAddress = "서울 강남구 테헤란로 1",
                detailAddress = null,
                latitude = BigDecimal("37.5"),
                longitude = BigDecimal("127.0"),
                source = PickupLocationSource.KAKAO,
            ),
        )
        val restaurant = restaurants.save(Restaurant("브랜드", location))
        val first = reviews.save(review(user, restaurant))
        first.softDelete(Instant.parse("2026-07-25T03:00:00Z"))
        reviews.save(first)
        val second = reviews.save(review(user, restaurant))
        entityManager.clear()

        assertThat(first.id).isPositive()
        assertThat(second.id).isPositive().isNotEqualTo(first.id)
        assertThat(reviews.findLatestSubmissionForUpdate(user.id, restaurant.id)?.reviewId).isEqualTo(second.id)
        assertThat(reviews.findOwnedActiveForUpdate(user.id, second.id)?.id).isEqualTo(second.id)
        assertThat(reviews.findOwnedActiveForUpdate(user.id, first.id)).isNull()
        assertThat(uniqueConstraintColumns("reviews"))
            .containsExactlyInAnyOrder("author_user_id", "restaurant_id", "current_slot")
    }

    private fun uniqueConstraintColumns(tableName: String): List<String> = entityManager
        .createNativeQuery(
            """
            select kcu.column_name
            from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu
              on tc.constraint_schema = kcu.constraint_schema
             and tc.table_name = kcu.table_name
             and tc.constraint_name = kcu.constraint_name
            where tc.constraint_schema = database()
              and tc.table_name = :tableName
              and tc.constraint_type = 'UNIQUE'
            order by kcu.ordinal_position
            """.trimIndent(),
            String::class.java,
        )
        .setParameter("tableName", tableName)
        .resultList
        .map { it.toString() }

    private fun review(user: User, restaurant: Restaurant) = Review(
        author = user,
        restaurant = restaurant,
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ReviewRatings(
            ReviewRating.GOOD,
            ReviewRating.GOOD,
            ReviewRating.GOOD,
            ReviewRating.GOOD,
            ReviewRating.NOT_OBSERVED,
            ReviewRating.GOOD,
        ),
        comment = null,
    )
}
