package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.PickupLocationSource
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.application.port.out.NewReviewPersistenceCommand
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewRatings
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
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
    fun `review mapping owns one active slot per author and restaurant with lazy parents`() {
        assertThat(Review::class.java.superclass).isEqualTo(BaseEntity::class.java)

        val table = Review::class.java.getAnnotation(Table::class.java)
        assertThat(table.uniqueConstraints.single().name)
            .isEqualTo("uk_reviews_author_restaurant_current_slot")
        assertThat(table.uniqueConstraints.single().columnNames)
            .containsExactly("author_user_id", "restaurant_id", "current_slot")

        assertLazyManyToOne(Review::class.java, "author")
        assertLazyManyToOne(Review::class.java, "restaurant")
        assertThat(Review::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    @Test
    fun `write lookups declare pessimistic locks`() {
        assertThat(
            SpringDataReviewRepository::class.java
                .getMethod(
                    "findLatestSubmissionForUpdate",
                    java.lang.Long.TYPE,
                    java.lang.Long.TYPE,
                    Pageable::class.java,
                )
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
        assertThat(
            SpringDataReviewRepository::class.java
                .getMethod(
                    "findOwnedActiveForUpdate",
                    java.lang.Long.TYPE,
                    java.lang.Long.TYPE,
                    ReviewVisibilityStatus::class.java,
                )
                .getAnnotation(Lock::class.java)
                .value,
        ).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }

    @Test
    fun `review adapter delegates latest ownership count and active cursor list paths`() {
        val auditTime = Instant.parse("2026-07-25T03:00:00Z")
        val review = review().also {
            it.id = 40L
            setAuditTimes(it, auditTime)
        }
        val calls = mutableListOf<String>()
        val reviews = fakeRepository(SpringDataReviewRepository::class.java) { method, arguments ->
            calls += method.name
            when (method.name) {
                "saveAndFlush" -> review
                "findLatestSubmissionForUpdate" -> {
                    assertPageable(arguments.last(), 1)
                    listOf(review)
                }
                "findOwnedActiveForUpdate" -> {
                    assertThat(arguments.last()).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    Optional.of(review)
                }
                "countByAuthorIdAndCreatedAtGreaterThanEqual" -> 3L
                "findAllByAuthorId" -> {
                    assertThat(arguments[1]).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    assertPageable(arguments.last(), 5)
                    listOf(review)
                }
                "findAllByAuthorIdBeforeCursor" -> {
                    assertThat(arguments[1]).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    assertThat(arguments[2]).isEqualTo(Instant.parse("2026-07-25T03:00:00Z"))
                    assertThat(arguments[3]).isEqualTo(40L)
                    assertPageable(arguments.last(), 5)
                    listOf(review)
                }
                else -> unexpected(method)
            }
        }
        val adapter = ReviewPersistenceAdapter(
            reviews,
            fakeEntityManager(review.author, review.restaurant),
        )

        assertThat(
            adapter.create(
                NewReviewPersistenceCommand(
                    authorUserId = 7L,
                    restaurantId = 10L,
                    visitMonth = review.visitMonth,
                    ratings = review.ratings,
                    comment = null,
                ),
            ).reviewId,
        ).isEqualTo(40L)
        assertThat(adapter.save(review)).isSameAs(review)
        val latest = adapter.findLatestSubmissionForUpdate(7L, 10L)!!
        assertThat(latest.reviewId).isEqualTo(40L)
        assertThat(latest.submittedAt).isEqualTo(auditTime)
        assertThat(latest.active).isTrue()
        assertThat(adapter.findOwnedActiveForUpdate(7L, 40L)).isSameAs(review)
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
            "saveAndFlush",
            "findLatestSubmissionForUpdate",
            "findOwnedActiveForUpdate",
            "countByAuthorIdAndCreatedAtGreaterThanEqual",
            "findAllByAuthorId",
            "findAllByAuthorIdBeforeCursor",
        )
    }

    @Test
    fun `soft deleted review remains the latest submission but is inactive`() {
        val review = review().also {
            it.id = 40L
            setAuditTimes(it, Instant.parse("2026-07-25T03:00:00Z"))
            it.softDelete(Instant.parse("2026-07-26T03:00:00Z"))
        }
        val reviews = fakeRepository(SpringDataReviewRepository::class.java) { method, _ ->
            when (method.name) {
                "findLatestSubmissionForUpdate" -> listOf(review)
                else -> unexpected(method)
            }
        }
        val adapter = ReviewPersistenceAdapter(reviews, fakeEntityManager(review.author, review.restaurant))

        val latest = adapter.findLatestSubmissionForUpdate(7L, 10L)!!
        assertThat(latest.submittedAt).isEqualTo(Instant.parse("2026-07-25T03:00:00Z"))
        assertThat(latest.active).isFalse()
    }

    private fun assertLazyManyToOne(type: Class<*>, fieldName: String) {
        val relation = type.getDeclaredField(fieldName).getAnnotation(ManyToOne::class.java)
        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isFalse()
        assertThat(relation.cascade).isEmpty()
    }

    private fun assertPageable(value: Any?, expectedSize: Int) {
        assertThat(value).isInstanceOf(Pageable::class.java)
        assertThat((value as Pageable).pageSize).isEqualTo(expectedSize)
    }

    private fun setAuditTimes(entity: BaseEntity, instant: Instant) {
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            BaseEntity::class.java.getDeclaredField(fieldName).also { field ->
                field.isAccessible = true
                field.set(entity, instant)
            }
        }
    }

    private fun fakeEntityManager(user: User, restaurant: Restaurant): EntityManager =
        fakeRepository(EntityManager::class.java) { method, arguments ->
            when (method.name) {
                "getReference" -> when (arguments[0]) {
                    User::class.java -> user
                    Restaurant::class.java -> restaurant
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
