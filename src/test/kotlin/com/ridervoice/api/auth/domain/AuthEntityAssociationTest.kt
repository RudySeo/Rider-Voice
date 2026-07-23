package com.ridervoice.api.auth.domain

import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthEntityAssociationTest {

    @Test
    fun `oauth account references its user with a lazy required association`() {
        assertLazyRequiredManyToOne(OAuthAccount::class.java, "user")
    }

    @Test
    fun `user session references its user and optional successor without reverse collections`() {
        assertLazyRequiredManyToOne(UserSession::class.java, "user")

        val successor = UserSession::class.java.getDeclaredField("rotatedToSession")
        assertThat(successor.getAnnotation(OneToOne::class.java).fetch).isEqualTo(FetchType.LAZY)
        assertThat(successor.getAnnotation(JoinColumn::class.java).name)
            .isEqualTo("rotated_to_session_id")
        assertThat(User::class.java.declaredFields.map { it.type })
            .noneMatch { Collection::class.java.isAssignableFrom(it) }
    }

    private fun assertLazyRequiredManyToOne(type: Class<*>, fieldName: String) {
        val field = type.getDeclaredField(fieldName)
        val relation = field.getAnnotation(ManyToOne::class.java)
        val joinColumn = field.getAnnotation(JoinColumn::class.java)

        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isFalse()
        assertThat(joinColumn.name).isEqualTo("user_id")
        assertThat(joinColumn.nullable).isFalse()
        assertThat(joinColumn.updatable).isFalse()
    }
}
