package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.RiderVerificationAttempt
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RiderVerificationAttemptRepository : JpaRepository<RiderVerificationAttempt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from RiderVerificationAttempt attempt where attempt.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): RiderVerificationAttempt?
}
