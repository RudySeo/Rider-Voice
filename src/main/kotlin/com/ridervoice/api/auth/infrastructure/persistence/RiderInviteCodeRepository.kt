package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.RiderInviteCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface RiderInviteCodeRepository : JpaRepository<RiderInviteCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from RiderInviteCode code where code.currentSlot = 1")
    fun findCurrentForUpdate(): RiderInviteCode?
}
