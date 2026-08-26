package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.MobileLoginGrant
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface MobileLoginGrantRepository : JpaRepository<MobileLoginGrant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from MobileLoginGrant grant where grant.codeHash = :codeHash")
    fun findByCodeHashForUpdate(@Param("codeHash") codeHash: String): Optional<MobileLoginGrant>
}
