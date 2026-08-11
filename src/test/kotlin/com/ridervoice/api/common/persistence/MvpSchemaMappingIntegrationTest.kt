package com.ridervoice.api.common.persistence

import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.lang.reflect.Field
import javax.sql.DataSource

@SpringBootTest
class MvpSchemaMappingIntegrationTest : MySqlIntegrationTest() {

    @Autowired private lateinit var entityManagerFactory: EntityManagerFactory
    @Autowired private lateinit var dataSource: DataSource

    @Test
    fun `every target entity uses BaseEntity Long identity and only lazy child to parent associations`() {
        val entityClasses = entityManagerFactory.metamodel.entities.map { it.javaType }.toSet()

        assertThat(entityClasses.map(Class<*>::getSimpleName)).containsExactlyInAnyOrderElementsOf(
            EXPECTED_ENTITY_NAMES,
        )
        entityClasses.forEach { entityClass ->
            assertThat(BaseEntity::class.java.isAssignableFrom(entityClass))
                .describedAs("${entityClass.simpleName} extends BaseEntity")
                .isTrue()
        }

        val idField = BaseEntity::class.java.getDeclaredField("id")
        assertThat(idField.type).isEqualTo(Long::class.javaPrimitiveType)
        assertThat(idField.getAnnotation(GeneratedValue::class.java).strategy)
            .isEqualTo(GenerationType.IDENTITY)

        val associationFields = entityClasses.flatMap { entityClass ->
            entityClass.declaredFields.filter { it.isAssociation() }
        }
        assertThat(associationFields).isNotEmpty
        associationFields.forEach { field ->
            val fetch = field.getAnnotation(ManyToOne::class.java)?.fetch
                ?: field.getAnnotation(OneToOne::class.java)?.fetch
            assertThat(fetch)
                .describedAs("${field.declaringClass.simpleName}.${field.name} fetch type")
                .isEqualTo(FetchType.LAZY)
        }
        assertThat(
            entityClasses.flatMap { it.declaredFields.asList() }
                .filter { it.isAnnotationPresent(OneToMany::class.java) || it.isAnnotationPresent(ManyToMany::class.java) },
        ).isEmpty()
    }

    @Test
    fun `hibernate update exposes target identity foreign key unique and query indexes`() {
        val jdbc = JdbcTemplate(dataSource)
        val targetTables = entityManagerFactory.metamodel.entities
            .map { it.javaType.getAnnotation(Table::class.java).name }
            .toSet()
        val identityColumns = jdbc.query(
            """
            select table_name, data_type, is_nullable, extra
            from information_schema.columns
            where table_schema = database()
              and column_name = 'id'
            """.trimIndent(),
        ) { resultSet, _ ->
            IdentityColumn(
                table = resultSet.getString("table_name"),
                dataType = resultSet.getString("data_type"),
                nullable = resultSet.getString("is_nullable"),
                extra = resultSet.getString("extra"),
            )
        }.filter { it.table in targetTables }

        assertThat(identityColumns.map(IdentityColumn::table)).containsExactlyInAnyOrderElementsOf(targetTables)
        identityColumns.forEach { column ->
            assertThat(column.dataType).describedAs("${column.table}.id data type").isEqualTo("bigint")
            assertThat(column.nullable).describedAs("${column.table}.id nullable").isEqualTo("NO")
            assertThat(column.extra).describedAs("${column.table}.id generation").contains("auto_increment")
        }

        val foreignKeys = jdbc.query(
            """
            select kcu.table_name,
                   kcu.column_name,
                   kcu.referenced_table_name,
                   rc.delete_rule
            from information_schema.key_column_usage kcu
            join information_schema.referential_constraints rc
              on rc.constraint_schema = kcu.constraint_schema
             and rc.constraint_name = kcu.constraint_name
             and rc.table_name = kcu.table_name
            where kcu.constraint_schema = database()
              and kcu.referenced_table_name is not null
            """.trimIndent(),
        ) { resultSet, _ ->
            ForeignKeySpec(
                table = resultSet.getString("table_name"),
                column = resultSet.getString("column_name"),
                referencedTable = resultSet.getString("referenced_table_name"),
                deleteRule = resultSet.getString("delete_rule"),
            )
        }
        assertThat(foreignKeys.map(ForeignKeySpec::identity)).containsAll(EXPECTED_FOREIGN_KEYS)
        foreignKeys.filter { it.identity in EXPECTED_FOREIGN_KEYS }.forEach { foreignKey ->
            assertThat(foreignKey.deleteRule)
                .describedAs("${foreignKey.table}.${foreignKey.column} delete rule")
                .isIn("RESTRICT", "NO ACTION")
        }

        val indexes = jdbc.query(
            """
            select table_name, index_name, non_unique, seq_in_index, column_name
            from information_schema.statistics
            where table_schema = database()
            order by table_name, index_name, seq_in_index
            """.trimIndent(),
        ) { resultSet, _ ->
            IndexColumn(
                table = resultSet.getString("table_name"),
                name = resultSet.getString("index_name"),
                nonUnique = resultSet.getBoolean("non_unique"),
                sequence = resultSet.getInt("seq_in_index"),
                column = resultSet.getString("column_name"),
            )
        }.groupBy { it.table to it.name }
            .map { (identity, columns) ->
                IndexSpec(
                    table = identity.first,
                    name = identity.second,
                    unique = !columns.first().nonUnique,
                    columns = columns.sortedBy(IndexColumn::sequence).map(IndexColumn::column),
                )
            }
        assertThat(indexes).containsAll(EXPECTED_INDEXES)
    }

    private fun Field.isAssociation(): Boolean =
        isAnnotationPresent(ManyToOne::class.java) || isAnnotationPresent(OneToOne::class.java)

    private data class IdentityColumn(
        val table: String,
        val dataType: String,
        val nullable: String,
        val extra: String,
    )

    private data class ForeignKeySpec(
        val table: String,
        val column: String,
        val referencedTable: String,
        val deleteRule: String = "",
    ) {
        val identity: Triple<String, String, String> = Triple(table, column, referencedTable)
    }

    private data class IndexColumn(
        val table: String,
        val name: String,
        val nonUnique: Boolean,
        val sequence: Int,
        val column: String,
    )

    private data class IndexSpec(
        val table: String,
        val name: String,
        val unique: Boolean,
        val columns: List<String>,
    )

    private companion object {
        val EXPECTED_ENTITY_NAMES = setOf(
            "User",
            "OAuthAccount",
            "UserSession",
            "PickupLocation",
            "Restaurant",
            "RestaurantExternalReference",
            "RestaurantPlatform",
            "Review",
            "ReviewReport",
            "RestaurantInfoReport",
            "ModerationAudit",
        )

        val EXPECTED_FOREIGN_KEYS = setOf(
            Triple("oauth_accounts", "user_id", "users"),
            Triple("user_sessions", "user_id", "users"),
            Triple("user_sessions", "rotated_to_session_id", "user_sessions"),
            Triple("restaurants", "pickup_location_id", "pickup_locations"),
            Triple("restaurants", "canonical_restaurant_id", "restaurants"),
            Triple("restaurant_external_references", "restaurant_id", "restaurants"),
            Triple("restaurant_platforms", "restaurant_id", "restaurants"),
            Triple("reviews", "author_user_id", "users"),
            Triple("reviews", "restaurant_id", "restaurants"),
            Triple("review_reports", "reporter_user_id", "users"),
            Triple("review_reports", "review_id", "reviews"),
            Triple("review_reports", "decided_by_user_id", "users"),
            Triple("restaurant_info_reports", "reporter_user_id", "users"),
            Triple("restaurant_info_reports", "restaurant_id", "restaurants"),
            Triple("restaurant_info_reports", "decided_by_user_id", "users"),
            Triple("moderation_audits", "actor_user_id", "users"),
        )

        val EXPECTED_INDEXES = listOf(
            IndexSpec("oauth_accounts", "uk_oauth_accounts_provider_subject", true, listOf("provider", "provider_subject")),
            IndexSpec("oauth_accounts", "uk_oauth_accounts_user_provider", true, listOf("user_id", "provider")),
            IndexSpec("user_sessions", "uk_user_sessions_refresh_token_hash", true, listOf("refresh_token_hash")),
            IndexSpec("user_sessions", "idx_user_sessions_user", false, listOf("user_id")),
            IndexSpec("user_sessions", "idx_user_sessions_active_expiry", false, listOf("revoked_at", "expires_at")),
            IndexSpec("pickup_locations", "uk_pickup_locations_location_key", true, listOf("location_key")),
            IndexSpec("pickup_locations", "idx_pickup_locations_normalized_address", false, listOf("normalized_address")),
            IndexSpec("restaurants", "uk_restaurants_pickup_location_normalized_name", true, listOf("pickup_location_id", "normalized_name")),
            IndexSpec("restaurants", "idx_restaurants_status_normalized_name", false, listOf("status", "normalized_name")),
            IndexSpec("restaurants", "idx_restaurants_canonical", false, listOf("canonical_restaurant_id")),
            IndexSpec("restaurant_external_references", "uk_restaurant_external_references_provider_place", true, listOf("provider", "external_place_id")),
            IndexSpec("restaurant_external_references", "idx_restaurant_external_references_restaurant", false, listOf("restaurant_id")),
            IndexSpec("restaurant_platforms", "idx_restaurant_platforms_restaurant", false, listOf("restaurant_id")),
            IndexSpec("reviews", "uk_reviews_author_restaurant_current_slot", true, listOf("author_user_id", "restaurant_id", "current_slot")),
            IndexSpec("reviews", "idx_reviews_author_restaurant_created", false, listOf("author_user_id", "restaurant_id", "created_at", "id")),
            IndexSpec("reviews", "idx_reviews_restaurant_visibility_created", false, listOf("restaurant_id", "current_slot", "visibility_status", "deleted_at", "created_at", "id")),
            IndexSpec("review_reports", "uk_review_reports_reporter_review", true, listOf("reporter_user_id", "review_id")),
            IndexSpec("review_reports", "idx_review_reports_status_created", false, listOf("status", "created_at", "id")),
            IndexSpec("restaurant_info_reports", "uk_restaurant_info_reports_reporter_restaurant", true, listOf("reporter_user_id", "restaurant_id")),
            IndexSpec("restaurant_info_reports", "idx_restaurant_info_reports_status_created", false, listOf("status", "created_at", "id")),
            IndexSpec("moderation_audits", "idx_moderation_audits_target_created", false, listOf("target_type", "target_id", "created_at", "id")),
        )
    }
}
