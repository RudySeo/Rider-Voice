package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.EnsureReviewWriterUseCase
import com.ridervoice.api.auth.application.port.`in`.RotateRiderInviteCodeCommand
import com.ridervoice.api.auth.application.port.`in`.RotateRiderInviteCodeUseCase
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderCommand
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderUseCase
import com.ridervoice.api.auth.application.port.out.RiderCodeHasher
import com.ridervoice.api.auth.application.port.out.RiderInviteCodeStore
import com.ridervoice.api.auth.application.port.out.RiderVerificationAttemptStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.RiderInviteCode
import com.ridervoice.api.auth.domain.RiderVerificationAttempt
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.common.error.AccessDeniedException
import com.ridervoice.api.common.error.AuthenticationRequiredException
import com.ridervoice.api.common.error.RiderVerificationFailedException
import com.ridervoice.api.common.error.RiderVerificationRateLimitException
import com.ridervoice.api.common.error.RiderVerificationUnavailableException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
class RiderAccessService(
    private val users: UserStore,
    private val codes: RiderInviteCodeStore,
    private val attempts: RiderVerificationAttemptStore,
    private val codeHasher: RiderCodeHasher,
    private val clock: Clock = Clock.systemUTC(),
) : VerifyRiderUseCase, RotateRiderInviteCodeUseCase, EnsureReviewWriterUseCase {

    @Transactional(
        noRollbackFor = [RiderVerificationFailedException::class, RiderVerificationRateLimitException::class],
    )
    override fun verify(command: VerifyRiderCommand): UserSummary {
        val user = activeUserForUpdate(command.userId)
        if (user.role != UserRole.USER) return user.toSummary()

        val now = clock.instant()
        val attempt = attempts.findByUserIdForUpdate(user.id)
        if (attempt?.isLockedAt(now) == true) {
            throw RiderVerificationRateLimitException(Duration.between(now, attempt.lockedUntil).seconds.coerceAtLeast(1))
        }
        val currentCode = codes.findCurrentForUpdate() ?: throw RiderVerificationUnavailableException()
        if (!codeHasher.matches(command.code, currentCode.codeHash)) {
            val updated = attempt ?: RiderVerificationAttempt(user)
            val lockedUntil = updated.registerFailure(now)
            attempts.saveAttempt(updated)
            if (lockedUntil != null) {
                throw RiderVerificationRateLimitException(Duration.between(now, lockedUntil).seconds)
            }
            throw RiderVerificationFailedException()
        }

        user.promoteToRider()
        users.saveUser(user)
        attempt?.also { it.clear(); attempts.saveAttempt(it) }
        return user.toSummary()
    }

    @Transactional
    override fun rotate(command: RotateRiderInviteCodeCommand) {
        val admin = activeUserForUpdate(command.adminUserId)
        if (admin.role != UserRole.ADMIN) throw AccessDeniedException()
        val now = clock.instant()
        codes.findCurrentForUpdate()?.also { it.revoke(now); codes.saveCode(it) }
        codes.saveCode(RiderInviteCode(admin, codeHasher.hash(command.code)))
    }

    @Transactional(readOnly = true)
    override fun ensureEligible(userId: Long) {
        val user = users.findUser(userId) ?: throw AuthenticationRequiredException()
        if (user.status != UserStatus.ACTIVE || user.role == UserRole.USER) throw AccessDeniedException()
    }

    private fun activeUserForUpdate(userId: Long): User = users.findUserForUpdate(userId)
        ?.takeIf { it.status == UserStatus.ACTIVE }
        ?: throw AuthenticationRequiredException()

    private fun User.toSummary() = UserSummary(id, status.name, role)
}
