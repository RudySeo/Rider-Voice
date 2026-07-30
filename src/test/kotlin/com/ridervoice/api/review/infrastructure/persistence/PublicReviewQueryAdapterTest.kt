package com.ridervoice.api.review.infrastructure.persistence

import com.ridervoice.api.review.application.model.ReviewCursor
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewRating
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import com.ridervoice.api.review.domain.VisitMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant

class PublicReviewQueryAdapterTest {

    @Test
    fun `adapter delegates reverse cursor and maps review and author activity`() {
        val row = TestPublicReviewProjection()
        val activity = TestPublicAuthorActivityProjection()
        val calls = mutableListOf<String>()
        val repository = fakeRepository { method, arguments ->
            calls += method.name
            when (method.name) {
                "findAllActiveByRestaurantId" -> {
                    assertThat(arguments[1]).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    assertPageable(arguments.last(), 3)
                    listOf(row)
                }
                "findAllActiveByRestaurantIdBeforeCursor" -> {
                    assertThat(arguments[1]).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    assertThat(arguments[2]).isEqualTo(Instant.parse("2026-07-25T03:00:00Z"))
                    assertThat(arguments[3]).isEqualTo(100L)
                    assertPageable(arguments.last(), 3)
                    listOf(row)
                }
                "findPublicAuthorActivities" -> {
                    assertThat(arguments[0]).isEqualTo(setOf(7L))
                    assertThat(arguments[1]).isEqualTo(ReviewVisibilityStatus.ACTIVE)
                    listOf(activity)
                }
                else -> unexpected(method)
            }
        }
        val adapter = PublicReviewQueryPersistenceAdapter(repository)

        val first = adapter.findActiveByRestaurantId(10L, null, 3).single()
        val after = adapter.findActiveByRestaurantId(
            10L,
            ReviewCursor(Instant.parse("2026-07-25T03:00:00Z"), 100L),
            3,
        ).single()
        val author = adapter.findAuthorActivities(setOf(7L)).single()

        assertThat(first.reviewId).isEqualTo(100L)
        assertThat(after).isEqualTo(first)
        assertThat(author.publicReviewCount).isEqualTo(8L)
        assertThat(adapter.findAuthorActivities(emptySet())).isEmpty()
        assertThat(calls).containsExactly(
            "findAllActiveByRestaurantId",
            "findAllActiveByRestaurantIdBeforeCursor",
            "findPublicAuthorActivities",
        )
    }

    @Test
    fun `queries expose only active non-deleted history in deterministic order and aggregate anonymous activity`() {
        val listQuery = queryText("findAllActiveByRestaurantId")
        val cursorQuery = queryText("findAllActiveByRestaurantIdBeforeCursor")
        val activityQuery = queryText("findPublicAuthorActivities")

        assertThat(listQuery).contains("review.restaurant.id = :restaurantId")
        assertThat(listQuery).contains("review.visibilityStatus = :visibilityStatus")
        assertThat(listQuery).contains("order by review.createdAt desc, review.id desc")
        assertThat(listQuery).contains("review.currentSlot is not null")
        assertThat(listQuery).contains("review.deletedAt is null")
        assertThat(cursorQuery).contains("review.createdAt < :cursorCreatedAt")
        assertThat(cursorQuery).contains("review.id < :cursorReviewId")
        assertThat(activityQuery).contains("min(review.createdAt)")
        assertThat(activityQuery).contains("count(review.id)")
        assertThat(activityQuery).contains("review.visibilityStatus = :visibilityStatus")
        assertThat(activityQuery).contains("review.currentSlot is not null")
        assertThat(activityQuery).contains("review.deletedAt is null")
    }

    private fun queryText(methodName: String): String =
        SpringDataReviewRepository::class.java.declaredMethods
            .single { it.name == methodName }
            .getAnnotation(Query::class.java)
            .value
            .replace(Regex("\\s+"), " ")

    private fun assertPageable(value: Any?, expectedSize: Int) {
        assertThat(value).isInstanceOf(Pageable::class.java)
        assertThat((value as Pageable).pageSize).isEqualTo(expectedSize)
    }

    private fun fakeRepository(
        handler: (Method, List<Any?>) -> Any?,
    ): SpringDataReviewRepository = SpringDataReviewRepository::class.java.cast(
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

    private data class TestPublicReviewProjection(
        override val reviewId: Long = 100L,
        override val authorUserId: Long = 7L,
        override val visitMonth: VisitMonth = VisitMonth.parse("2026-07"),
        override val pickupSpaceCleanliness: ReviewRating = ReviewRating.GOOD,
        override val packagingStability: ReviewRating = ReviewRating.VERY_GOOD,
        override val orderReadiness: ReviewRating = ReviewRating.GOOD,
        override val handoffAccuracy: ReviewRating = ReviewRating.GOOD,
        override val staffInteraction: ReviewRating = ReviewRating.NOT_OBSERVED,
        override val riderRespect: ReviewRating = ReviewRating.GOOD,
        override val comment: String? = "공개 의견",
        override val commentModerationStatus: ReviewCommentStatus = ReviewCommentStatus.PUBLISHED,
        override val createdAt: Instant = Instant.parse("2026-07-25T03:00:00Z"),
    ) : PublicReviewProjection

    private data class TestPublicAuthorActivityProjection(
        override val authorUserId: Long = 7L,
        override val firstPublicReviewAt: Instant = Instant.parse("2026-05-25T03:00:00Z"),
        override val publicReviewCount: Long = 8L,
    ) : PublicAuthorActivityProjection
}
