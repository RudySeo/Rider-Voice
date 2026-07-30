package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.moderation.application.port.out.NewReviewReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.NewRestaurantInfoReportPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantInfoReportRepository
import com.ridervoice.api.moderation.application.port.out.ReviewReportRepository
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportReason
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
@Tag("integration")
class ModerationPersistenceSchemaIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var users: UserStore
    @Autowired private lateinit var pickupLocations: PickupLocationRepository
    @Autowired private lateinit var restaurants: RestaurantRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var reviewReports: ReviewReportRepository
    @Autowired private lateinit var restaurantInfoReports: RestaurantInfoReportRepository
    @Autowired private lateinit var entityManager: EntityManager

    @Test
    fun `reporter cannot report the same review twice`() {
        val fixture = fixture()
        val command = NewReviewReportPersistenceCommand(
            reporterUserId = fixture.user.id,
            reviewId = fixture.review.id,
            reason = ReviewReportReason.SPAM,
            details = null,
        )
        reviewReports.create(command)

        assertThatThrownBy { reviewReports.create(command) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `reporter cannot report the same restaurant twice`() {
        val fixture = fixture()
        val command = NewRestaurantInfoReportPersistenceCommand(
            reporterUserId = fixture.user.id,
            restaurantId = fixture.review.restaurant.id,
            reason = RestaurantInfoReportReason.DUPLICATE,
            details = null,
        )
        restaurantInfoReports.create(command)

        assertThatThrownBy { restaurantInfoReports.create(command) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `moderation tables retain expected foreign keys without cascade delete`() {
        val foreignKeys = foreignKeys(
            "review_reports",
            "restaurant_info_reports",
            "moderation_audits",
        )

        assertThat(foreignKeys.map { Triple(it.tableName, it.constraintName, it.referencedTableName) }).contains(
            Triple("review_reports", "fk_review_reports_reporter_user", "users"),
            Triple("review_reports", "fk_review_reports_review", "reviews"),
            Triple("restaurant_info_reports", "fk_restaurant_info_reports_restaurant", "restaurants"),
            Triple("moderation_audits", "fk_moderation_audits_actor_user", "users"),
        )
        assertThat(foreignKeys.map(ForeignKeyRow::deleteRule))
            .allMatch { it == "RESTRICT" || it == "NO ACTION" }
    }

    @Test
    fun `moderation audit text columns retain reasons and state snapshots beyond 255 characters`() {
        val columnTypes = entityManager.createNativeQuery(
            """
            select column_name, data_type
            from information_schema.columns
            where table_schema = database()
              and table_name = 'moderation_audits'
              and column_name in ('reason', 'before_state', 'after_state')
            """.trimIndent(),
        ).resultList.associate { row ->
            row as Array<*>
            row[0].toString() to row[1].toString()
        }

        assertThat(columnTypes["reason"]).isEqualTo("text")
        assertThat(columnTypes["before_state"]).isEqualTo("mediumtext")
        assertThat(columnTypes["after_state"]).isEqualTo("mediumtext")
    }

    private fun fixture(): Fixture {
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
        val review = reviews.save(
            Review(
                author = user,
                restaurant = restaurant,
                visitMonth = VisitMonth.parse("2026-07"),
                ratings = ReviewRatings(
                    pickupSpaceCleanliness = ReviewRating.GOOD,
                    packagingStability = ReviewRating.GOOD,
                    orderReadiness = ReviewRating.GOOD,
                    handoffAccuracy = ReviewRating.GOOD,
                    staffInteraction = ReviewRating.NOT_OBSERVED,
                    riderRespect = ReviewRating.GOOD,
                ),
                comment = null,
            ),
        )
        return Fixture(user, review)
    }

    @Suppress("UNCHECKED_CAST")
    private fun foreignKeys(vararg tableNames: String): List<ForeignKeyRow> = entityManager
        .createNativeQuery(
            """
            select rc.table_name,
                   rc.constraint_name,
                   rc.referenced_table_name,
                   rc.delete_rule
            from information_schema.referential_constraints rc
            where rc.constraint_schema = database()
              and rc.table_name in (:tableNames)
            """.trimIndent(),
        )
        .setParameter("tableNames", tableNames.toList())
        .resultList
        .map { row ->
            row as Array<Any>
            ForeignKeyRow(
                tableName = row[0].toString(),
                constraintName = row[1].toString(),
                referencedTableName = row[2].toString(),
                deleteRule = row[3].toString(),
            )
        }

    private data class Fixture(val user: User, val review: Review)

    private data class ForeignKeyRow(
        val tableName: String,
        val constraintName: String,
        val referencedTableName: String,
        val deleteRule: String,
    )
}
