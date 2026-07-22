package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.UserSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserSessionRepository : JpaRepository<UserSession, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): Optional<UserSession>
}
