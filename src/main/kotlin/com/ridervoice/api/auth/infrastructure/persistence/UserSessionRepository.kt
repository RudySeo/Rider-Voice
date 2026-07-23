package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.UserSession
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserSessionRepository : JpaRepository<UserSession, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): Optional<UserSession>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session where session.refreshTokenHash = :refreshTokenHash")
    fun findByRefreshTokenHashForUpdate(
        @Param("refreshTokenHash") refreshTokenHash: String,
    ): Optional<UserSession>
}
