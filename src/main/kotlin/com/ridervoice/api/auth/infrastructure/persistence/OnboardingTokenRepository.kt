package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OnboardingToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface OnboardingTokenRepository : JpaRepository<OnboardingToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from OnboardingToken token where token.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): Optional<OnboardingToken>
}
