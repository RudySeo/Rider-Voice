package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.port.out.AuthorRestaurantReviewStateSnapshot
import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.VisitMonth
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.LockModeType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class ReviewPersistenceAdapterTest {

    @Test
    fun `review state mapping owns the only author restaurant uniqueness and lazy parent relations`() {
        assertThat(AuthorRestaurantReviewState::class.java.superclass).isEqualTo(BaseEntity::class.java)

        val stateTable = AuthorRestaurantReviewState::class.java.getAnnotation(Table::class.java)
        assertThat(stateTable.uniqueConstraints.single().name)
            .isEqualTo("uk_author_restaurant_review_states_author_restaurant")
        assertThat(stateTable.uniqueConstraints.single().columnNames)
            .containsExactly("author_user_id", "restaurant_id")
        assertThat(Review::class.java.getAnnotation(Table::class.java).uniqueConstraints).isEmpty()

        assertLazyManyToOne(AuthorRestaurantReviewState::class.java, "author", optional = false)
        assertLazyManyToOne(AuthorRestaurantReviewState::class.java, "restaurant", optional = false)
        assertLazyManyToOne(AuthorRestaurantReviewState::class.java, "currentReview", optional = true)
        assertThat(AuthorRestaurantReviewState::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    @Test
    fun `write lookups declare pessimistic locks`() {
        assertThat(
            SpringDataAuthorRestaurantReviewStateRepository::class.java
                .getMethod("findForUpdate", java.lang.Long.TYPE, java.lang.Long.TYPE)
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
        assertThat(
            SpringDataReviewRepository::class.java
                .getMethod("findOwnedCurrentForUpdate", java.lang.Long.TYPE, java.lang.Long.TYPE)
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }

    @Test
    fun `review adapter delegates current ownership count and both cursor list paths`() {
        val review = review().also { it.id = 40L }
        val calls = mutableListOf<String>()
        val reviews = fakeRepository(SpringDataReviewRepository::class.java) { method, arguments ->
            calls += method.name
            when (method.name) {
                "saveAndFlush" -> review
                "findOwnedCurrentForUpdate" -> Optional.of(review)
                "delete" -> null
                "countByAuthorIdAndCreatedAtGreaterThanEqual" -> 3L
                "findAllByAuthorId" -> {
                    assertPageable(arguments.last(), 5)
                    listOf(review)
                }
                "findAllByAuthorIdBeforeCursor" -> {
                    assertThat(arguments[1]).isEqualTo(Instant.parse("2026-07-25T03:00:00Z"))
                    assertThat(arguments[2]).isEqualTo(40L)
                    assertPageable(arguments.last(), 5)
                    listOf(review)
                }
                else -> unexpected(method)
            }
        }
        val adapter = ReviewPersistenceAdapter(reviews)

        assertThat(adapter.save(review)).isSameAs(review)
        assertThat(adapter.findOwnedCurrentForUpdate(7L, 40L)).isSameAs(review)
        adapter.delete(review)
        assertThat(adapter.countByAuthorUserIdSince(7L, Instant.parse("2026-07-24T03:00:00Z")))
            .isEqualTo(3L)
        assertThat(adapter.findByAuthorUserId(7L, null, 5)).containsExactly(review)
        assertThat(
            adapter.findByAuthorUserId(
                7L,
                ReviewCursor(Instant.parse("2026-07-25T03:00:00Z"), 40L),
                5,
            ),
        ).containsExactly(review)
        assertThat(calls).containsExactly(
            "saveAndFlush",
            "findOwnedCurrentForUpdate",
            "delete",
            "countByAuthorIdAndCreatedAtGreaterThanEqual",
            "findAllByAuthorId",
            "findAllByAuthorIdBeforeCursor",
        )
    }

    @Test
    fun `state adapter maps snapshots without cascading review lifecycle`() {
        val user = User().also { it.id = 7L }
        val restaurant = restaurant().also { it.id = 10L }
        val currentReview = review(user, restaurant).also { it.id = 40L }
        val storedState = AuthorRestaurantReviewState(
            author = user,
            restaurant = restaurant,
            lastSubmittedAt = Instant.parse("2026-07-25T03:00:00Z"),
            lastSequence = 2L,
            currentReview = currentReview,
        ).also { it.id = 30L }
        val states = fakeRepository(SpringDataAuthorRestaurantReviewStateRepository::class.java) {
                method, _ ->
            when (method.name) {
                "findForUpdate" -> Optional.of(storedState)
                "findById" -> Optional.of(storedState)
                "saveAndFlush" -> storedState
                else -> unexpected(method)
            }
        }
        val entityManager = fakeEntityManager(user, restaurant, currentReview)
        val adapter = AuthorRestaurantReviewStatePersistenceAdapter(states, entityManager)

        assertThat(adapter.findForUpdate(7L, 10L)).isEqualTo(
            AuthorRestaurantReviewStateSnapshot(30L, 7L, 10L, storedState.lastSubmittedAt, 2L, 40L),
        )
        val cleared = adapter.save(
            AuthorRestaurantReviewStateSnapshot(
                stateId = 30L,
                authorUserId = 7L,
                restaurantId = 10L,
                lastSubmittedAt = Instant.parse("2026-07-26T03:00:00Z"),
                lastSequence = 3L,
                currentReviewId = null,
            ),
        )

        assertThat(cleared.currentReviewId).isNull()
        assertThat(cleared.lastSequence).isEqualTo(3L)
        assertThat(storedState.currentReview).isNull()
        assertThat(AuthorRestaurantReviewState::class.java.getDeclaredField("currentReview")
            .getAnnotation(ManyToOne::class.java).cascade).isEmpty()
    }

    private fun assertLazyManyToOne(type: Class<*>, fieldName: String, optional: Boolean) {
        val relation = type.getDeclaredField(fieldName).getAnnotation(ManyToOne::class.java)
        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isEqualTo(optional)
        assertThat(relation.cascade).isEmpty()
    }

    private fun assertPageable(value: Any?, expectedSize: Int) {
        assertThat(value).isInstanceOf(Pageable::class.java)
        assertThat((value as Pageable).pageSize).isEqualTo(expectedSize)
    }

    private fun fakeEntityManager(user: User, restaurant: Restaurant, review: Review): EntityManager =
        fakeRepository(EntityManager::class.java) { method, arguments ->
            when (method.name) {
                "getReference" -> when (arguments[0]) {
                    User::class.java -> user
                    Restaurant::class.java -> restaurant
                    Review::class.java -> review
                    else -> unexpected(method)
                }
                else -> unexpected(method)
            }
        }

    private fun review(
        user: User = User().also { it.id = 7L },
        restaurant: Restaurant = restaurant().also { it.id = 10L },
    ) = Review(
        author = user,
        restaurant = restaurant,
        visitMonth = VisitMonth.parse("2026-07"),
        ratings = ReviewRatings(
            pickupSpaceCleanliness = ReviewRating.GOOD,
            packagingStability = ReviewRating.VERY_GOOD,
            orderReadiness = ReviewRating.GOOD,
            handoffAccuracy = ReviewRating.GOOD,
            staffInteraction = ReviewRating.NOT_OBSERVED,
            riderRespect = ReviewRating.GOOD,
        ),
        comment = null,
        sequence = 1L,
    )

    private fun restaurant() = Restaurant(
        "브랜드",
        PickupLocation(
            standardAddress = "서울 강남구 테헤란로 1",
            detailAddress = null,
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            source = PickupLocationSource.KAKAO,
        ),
    )

    private fun <T> fakeRepository(type: Class<T>, handler: (Method, List<Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Fake${type.simpleName}"
                else -> handler(method, arguments?.toList().orEmpty())
            }
        },
    )

    private fun unexpected(method: Method): Nothing = error("Unexpected method: ${method.name}")
}
