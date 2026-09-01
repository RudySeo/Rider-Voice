package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.restaurant.application.port.out.PickupLocationRepository
import com.ridervoice.api.restaurant.application.port.out.RestaurantRepository
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.port.out.ReviewRepository
import com.ridervoice.api.review.application.port.out.AggregateReviewQuery
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
    @Autowired private lateinit var aggregateReviews: AggregateReviewQuery
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

    @Test
    fun `batch aggregate query counts distinct active authors by restaurant`() {
        val location = pickupLocations.save(
            PickupLocation(
                standardAddress = "서울 강남구 배치 집계로 1",
                detailAddress = null,
                latitude = BigDecimal("37.5"),
                longitude = BigDecimal("127.0"),
                source = PickupLocationSource.KAKAO,
            ),
        )
        val publishedRestaurant = restaurants.save(Restaurant("공개 브랜드", location))
        val collectingRestaurant = restaurants.save(Restaurant("수집 브랜드", location))
        val noReviewRestaurant = restaurants.save(Restaurant("리뷰 없음 브랜드", location))
        val authors = (1..5).map { users.saveUser(User()) }

        authors.forEach { author -> reviews.save(review(author, publishedRestaurant)) }
        authors.take(4).forEach { author -> reviews.save(review(author, collectingRestaurant)) }
        reviews.save(review(authors.last(), collectingRestaurant).also(Review::exclude))
        entityManager.flush()
        entityManager.clear()

        val counts = aggregateReviews.countDistinctCurrentActiveAuthorsByRestaurantIds(
            setOf(publishedRestaurant.id, collectingRestaurant.id, noReviewRestaurant.id),
        )

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
            mapOf(publishedRestaurant.id to 5, collectingRestaurant.id to 4),
        )
        assertThat(counts).doesNotContainKey(noReviewRestaurant.id)
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
