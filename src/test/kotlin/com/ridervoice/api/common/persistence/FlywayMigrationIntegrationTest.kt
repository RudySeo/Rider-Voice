package com.ridervoice.api.common.persistence

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@Tag("migration")
@ActiveProfiles("migration-test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FlywayMigrationIntegrationTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val flyway: Flyway,
    private val entityManagerFactory: EntityManagerFactory,
) {

    @Test
    fun `all migrations apply to an empty database and Hibernate validates all mappings`() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
            """.trimIndent(),
            String::class.java,
        ).toSet()

        assertThat(entityManagerFactory.isOpen).isTrue()
        assertThat(tables).containsAll(DOMAIN_TABLES + "flyway_schema_history")
        assertThat(successfulMigrationVersions()).containsExactly("1", "2")
    }

    @Test
    fun `running Flyway again does not change an up-to-date schema`() {
        val result = flyway.migrate()

        assertThat(result.migrationsExecuted).isZero()
        assertThat(successfulMigrationVersions()).containsExactly("1", "2")
    }

    @Test
    fun `migrations preserve identity keys and critical unique constraints`() {
        val identityTables = jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND column_name = 'id'
              AND data_type = 'bigint'
              AND extra LIKE '%auto_increment%'
            """.trimIndent(),
            String::class.java,
        ).toSet()
        val uniqueConstraints = jdbcTemplate.queryForList(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND constraint_type = 'UNIQUE'
            """.trimIndent(),
            String::class.java,
        ).toSet()

        assertThat(identityTables).containsAll(DOMAIN_TABLES)
        assertThat(uniqueConstraints).contains(
            "uk_oauth_accounts_provider_subject",
            "uk_oauth_accounts_user_provider",
            "uk_user_sessions_refresh_token_hash",
            "uk_pickup_locations_location_key",
            "uk_restaurants_pickup_location_brand_name",
            "uk_restaurants_kakao_place_id",
            "uk_reviews_author_restaurant_current_slot",
            "uk_review_reports_reporter_review",
            "uk_restaurant_info_reports_reporter_restaurant",
            "uk_mobile_login_grants_code_hash",
        )
    }

    private fun successfulMigrationVersions(): List<String> = jdbcTemplate.queryForList(
        """
        SELECT version
        FROM flyway_schema_history
        WHERE success = TRUE
        ORDER BY installed_rank
        """.trimIndent(),
        String::class.java,
    ).filterNotNull()

    private companion object {
        val DOMAIN_TABLES = setOf(
            "users",
            "oauth_accounts",
            "user_sessions",
            "pickup_locations",
            "restaurants",
            "restaurant_platforms",
            "reviews",
            "review_reports",
            "restaurant_info_reports",
            "moderation_audits",
            "mobile_login_grants",
        )
    }
}
