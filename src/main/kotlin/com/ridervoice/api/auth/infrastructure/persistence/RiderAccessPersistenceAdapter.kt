package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.application.port.out.RiderInviteCodeStore
import com.ridervoice.api.auth.application.port.out.RiderVerificationAttemptStore
import com.ridervoice.api.auth.domain.RiderInviteCode
import com.ridervoice.api.auth.domain.RiderVerificationAttempt
import org.springframework.stereotype.Component

@Component
class RiderAccessPersistenceAdapter(
    private val codes: RiderInviteCodeRepository,
    private val attempts: RiderVerificationAttemptRepository,
) : RiderInviteCodeStore, RiderVerificationAttemptStore {
    override fun findCurrentForUpdate(): RiderInviteCode? = codes.findCurrentForUpdate()
    override fun saveCode(code: RiderInviteCode): RiderInviteCode = codes.save(code)
    override fun findByUserIdForUpdate(userId: Long): RiderVerificationAttempt? = attempts.findByUserIdForUpdate(userId)
    override fun saveAttempt(attempt: RiderVerificationAttempt): RiderVerificationAttempt = attempts.save(attempt)
}
