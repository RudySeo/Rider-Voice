package com.ridervoice.api.auth.infrastructure.persistence

import jakarta.persistence.LockModeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Lock

class UserSessionPersistenceContractTest {

    @Test
    fun `refresh token hash lookup uses a pessimistic write lock`() {
        val method = UserSessionRepository::class.java.getDeclaredMethod(
            "findByRefreshTokenHashForUpdate",
            String::class.java,
        )

        assertThat(method.getAnnotation(Lock::class.java)?.value)
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }
}
