package com.ridervoice.api.auth.infrastructure.persistence

import jakarta.persistence.LockModeType
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
    fun `migration defines onboarding token integrity constraints and cleanup index`() {
        val migration = requireNotNull(
            javaClass.getResource("/db/migration/V4__onboarding_tokens.sql"),
        ).readText()

        assertThat(migration).contains("id BINARY(16) PRIMARY KEY")
        assertThat(migration).contains("user_id BINARY(16) NOT NULL")
        assertThat(migration).contains("CONSTRAINT uk_onboarding_tokens_token_hash UNIQUE (token_hash)")
        assertThat(migration).contains("CONSTRAINT fk_onboarding_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)")
        assertThat(migration).contains(
            "CREATE INDEX idx_onboarding_tokens_active_expiry ON onboarding_tokens (consumed_at, expires_at)",
        )
    }
}
