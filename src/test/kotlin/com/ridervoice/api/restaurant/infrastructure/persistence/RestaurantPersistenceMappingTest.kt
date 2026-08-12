package com.ridervoice.api.restaurant.infrastructure.persistence

import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RestaurantPersistenceMappingTest {

    @Test
    fun `database mappings declare the three identity unique constraints`() {
        assertUniqueConstraint(
            PickupLocation::class.java,
            "uk_pickup_locations_location_key",
            "location_key",
        )
        assertUniqueConstraint(
            Restaurant::class.java,
            "uk_restaurants_pickup_location_brand_name",
            "pickup_location_id",
            "brand_name",
        )
        assertUniqueConstraint(
            RestaurantExternalReference::class.java,
            "uk_restaurant_external_references_provider_place",
            "provider",
            "external_place_id",
        )
    }

    @Test
    fun `database mappings retain search status canonical and foreign key indexes`() {
        assertIndexes(
            PickupLocation::class.java,
            "idx_pickup_locations_normalized_address" to "normalized_address",
        )
        assertIndexes(
            Restaurant::class.java,
            "idx_restaurants_status_brand_name" to "status, brand_name",
            "idx_restaurants_canonical" to "canonical_restaurant_id",
        )
        assertIndexes(
            RestaurantExternalReference::class.java,
            "idx_restaurant_external_references_restaurant" to "restaurant_id",
        )
        assertIndexes(
            RestaurantPlatform::class.java,
            "idx_restaurant_platforms_restaurant" to "restaurant_id",
        )
    }

    private fun assertUniqueConstraint(type: Class<*>, name: String, vararg columns: String) {
        val constraint = type.getAnnotation(Table::class.java).uniqueConstraints.single { it.name == name }

        assertThat(constraint.columnNames).containsExactly(*columns)
    }

    private fun assertIndexes(type: Class<*>, vararg expected: Pair<String, String>) {
        val indexes = type.getAnnotation(Table::class.java).indexes.associate { it.name to it.columnList }

        assertThat(indexes).containsAllEntriesOf(expected.toMap())
    }
}
