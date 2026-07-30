package com.ridervoice.api.restaurant.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RestaurantEntityAssociationTest {

    @Test
    fun `all restaurant entities use the shared Long identity base entity`() {
        assertThat(
            listOf(
                PickupLocation::class.java,
                Restaurant::class.java,
                RestaurantExternalReference::class.java,
                RestaurantPlatform::class.java,
            ),
        ).allMatch { it.superclass == BaseEntity::class.java }
    }

    @Test
    fun `child to parent associations are unidirectional lazy and do not cascade removal`() {
        assertLazyManyToOne(Restaurant::class.java, "pickupLocation", "pickup_location_id", optional = false)
        assertLazyManyToOne(Restaurant::class.java, "canonicalRestaurant", "canonical_restaurant_id", optional = true)
        assertLazyManyToOne(RestaurantExternalReference::class.java, "restaurant", "restaurant_id", optional = false)
        assertLazyManyToOne(RestaurantPlatform::class.java, "restaurant", "restaurant_id", optional = false)

        assertThat(PickupLocation::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
        assertThat(Restaurant::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    private fun assertLazyManyToOne(
        type: Class<*>,
        fieldName: String,
        joinColumnName: String,
        optional: Boolean,
    ) {
        val field = type.getDeclaredField(fieldName)
        val relation = field.getAnnotation(ManyToOne::class.java)
        val joinColumn = field.getAnnotation(JoinColumn::class.java)

        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isEqualTo(optional)
        assertThat(relation.cascade).isEmpty()
        assertThat(joinColumn.name).isEqualTo(joinColumnName)
        assertThat(joinColumn.nullable).isEqualTo(optional)
    }
}
