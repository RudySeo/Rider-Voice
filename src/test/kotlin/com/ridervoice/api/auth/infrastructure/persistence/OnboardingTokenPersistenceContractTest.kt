package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OnboardingToken
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.LockModeType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Lock

class OnboardingTokenPersistenceContractTest {

    @Test
    fun `token hash lookup uses a pessimistic write lock`() {
        val method = OnboardingTokenRepository::class.java.getDeclaredMethod(
            "findByTokenHashForUpdate",
            String::class.java,
        )

        assertThat(method.getAnnotation(Lock::class.java)?.value)
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }

    @Test
    fun `onboarding token maps its user lazily and keeps the cleanup index`() {
        val user = OnboardingToken::class.java.getDeclaredField("user")
        val relation = user.getAnnotation(ManyToOne::class.java)
        val joinColumn = user.getAnnotation(JoinColumn::class.java)
        val table = OnboardingToken::class.java.getAnnotation(Table::class.java)

        assertThat(relation.fetch).isEqualTo(FetchType.LAZY)
        assertThat(relation.optional).isFalse()
        assertThat(joinColumn.name).isEqualTo("user_id")
        assertThat(joinColumn.nullable).isFalse()
        assertThat(joinColumn.updatable).isFalse()
        assertThat(table.indexes.map(Index::name))
            .contains("idx_onboarding_tokens_active_expiry")
    }
}
