package com.ridervoice.api.review.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewEntityAssociationTest {

    @Test
    fun `review uses the shared Long identity base entity`() {
        assertThat(Review::class.java.superclass).isEqualTo(BaseEntity::class.java)
    }

    @Test
    fun `review keeps only lazy child to parent associations`() {
        assertLazyManyToOne("author", "author_user_id")
        assertLazyManyToOne("restaurant", "restaurant_id")

        assertThat(Review::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    private fun assertLazyManyToOne(fieldName: String, joinColumnName: String) {
        val field = Review::class.java.getDeclaredField(fieldName)
        val relation = field.getAnnotation(ManyToOne::class.java)
        val joinColumn = field.getAnnotation(JoinColumn::class.java)

        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isFalse()
        assertThat(relation.cascade).isEmpty()
        assertThat(joinColumn.name).isEqualTo(joinColumnName)
        assertThat(joinColumn.nullable).isFalse()
    }
}
