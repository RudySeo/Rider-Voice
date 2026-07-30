package com.ridervoice.api.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

class BaseEntityTest {

    @Test
    fun `base entity defines a database generated Long identity`() {
        val id = BaseEntity::class.java.getDeclaredField("id")

        assertThat(id.type).isEqualTo(Long::class.javaPrimitiveType)
        assertThat(id.isAnnotationPresent(Id::class.java)).isTrue()
        assertThat(id.getAnnotation(GeneratedValue::class.java).strategy)
            .isEqualTo(GenerationType.IDENTITY)
    }

    @Test
    fun `base entity defines immutable creation and mutable update instants`() {
        assertThat(BaseEntity::class.java).hasAnnotation(MappedSuperclass::class.java)

        val createdAt = BaseEntity::class.java.getDeclaredField("createdAt")
        val updatedAt = BaseEntity::class.java.getDeclaredField("updatedAt")

        assertThat(createdAt.type).isEqualTo(Instant::class.java)
        assertThat(createdAt.isAnnotationPresent(CreatedDate::class.java)).isTrue()
        assertThat(createdAt.getAnnotation(Column::class.java).updatable).isFalse()
        assertThat(createdAt.getAnnotation(Column::class.java).nullable).isFalse()

        assertThat(updatedAt.type).isEqualTo(Instant::class.java)
        assertThat(updatedAt.isAnnotationPresent(LastModifiedDate::class.java)).isTrue()
        assertThat(updatedAt.getAnnotation(Column::class.java).nullable).isFalse()
    }
}
