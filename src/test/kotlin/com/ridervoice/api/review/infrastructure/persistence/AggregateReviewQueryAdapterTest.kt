package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant

class AggregateReviewQueryAdapterTest {

    @Test
    fun `brand and location queries retain the latest current active review per author deterministically`() {
        val older = projection(40L, 7L, "2026-07-25T03:00:00Z")
        val sameTimeHigherId = projection(41L, 7L, "2026-07-25T03:00:00Z")
        val newest = projection(39L, 7L, "2026-07-26T03:00:00Z")
        val otherAuthor = projection(50L, 8L, "2026-07-24T03:00:00Z")
        val sameTimeLowerIdOtherAuthor = projection(49L, 8L, "2026-07-24T03:00:00Z")
        val repository = fakeRepository { method, arguments ->
            assertThat(arguments.last()).isEqualTo(ReviewVisibilityStatus.ACTIVE)
            when (method.name) {
                "findCurrentAggregateRowsByRestaurantId" ->
                    listOf(otherAuthor, sameTimeHigherId, older, sameTimeLowerIdOtherAuthor, newest)
                "findCurrentAggregateRowsByPickupLocationId" ->
                    listOf(older, sameTimeLowerIdOtherAuthor, otherAuthor, sameTimeHigherId, newest)
                else -> unexpected(method)
            }
        }
        val adapter = AggregateReviewQueryPersistenceAdapter(repository)

        assertThat(adapter.findCurrentActiveByRestaurantId(10L).map { it.reviewId })
            .containsExactly(39L, 50L)
        assertThat(adapter.findLatestCurrentActiveByPickupLocationId(20L).map { it.reviewId })
            .containsExactly(39L, 50L)
    }

    @Test
    fun `persistence queries select only active current slots from reviews`() {
        val restaurantQuery = queryText("findCurrentAggregateRowsByRestaurantId")
        val locationQuery = queryText("findCurrentAggregateRowsByPickupLocationId")

        assertThat(restaurantQuery).contains("from Review review")
        assertThat(restaurantQuery).contains("review.visibilityStatus = :visibilityStatus")
        assertThat(restaurantQuery).contains("review.restaurant.id = :restaurantId")
        assertThat(restaurantQuery).contains("review.currentSlot is not null")
        assertThat(restaurantQuery).contains("review.deletedAt is null")
        assertThat(locationQuery).contains("from Review review")
        assertThat(locationQuery).contains("review.visibilityStatus = :visibilityStatus")
        assertThat(locationQuery).contains("review.restaurant.pickupLocation.id = :pickupLocationId")
        assertThat(locationQuery).contains("review.currentSlot is not null")
        assertThat(locationQuery).contains("review.deletedAt is null")
    }

    private fun queryText(methodName: String): String =
        SpringDataReviewRepository::class.java.declaredMethods
            .single { it.name == methodName }
            .getAnnotation(Query::class.java)
            .value
            .replace(Regex("\\s+"), " ")

    private fun projection(
        reviewId: Long,
        authorUserId: Long,
        createdAt: String,
    ) = TestAggregateReviewProjection(reviewId, authorUserId, Instant.parse(createdAt))

    private fun fakeRepository(
        handler: (Method, List<Any?>) -> Any?,
    ): SpringDataReviewRepository =
        SpringDataReviewRepository::class.java.cast(
            Proxy.newProxyInstance(
                SpringDataReviewRepository::class.java.classLoader,
                arrayOf(SpringDataReviewRepository::class.java),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "equals" -> proxy === arguments?.singleOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "FakeSpringDataReviewRepository"
                    else -> handler(method, arguments?.toList().orEmpty())
                }
            },
        )

    private fun unexpected(method: Method): Nothing = error("Unexpected method: ${method.name}")

    private data class TestAggregateReviewProjection(
        override val reviewId: Long,
        override val authorUserId: Long,
        override val createdAt: Instant,
        override val pickupSpaceCleanliness: ReviewRating = ReviewRating.GOOD,
        override val packagingStability: ReviewRating = ReviewRating.VERY_GOOD,
        override val orderReadiness: ReviewRating = ReviewRating.GOOD,
        override val handoffAccuracy: ReviewRating = ReviewRating.GOOD,
        override val staffInteraction: ReviewRating = ReviewRating.NOT_OBSERVED,
        override val riderRespect: ReviewRating = ReviewRating.GOOD,
    ) : AggregateReviewProjection
}
