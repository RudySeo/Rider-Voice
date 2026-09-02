package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Index
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.Instant

@Entity
@Table(
    name = "rider_verification_attempts",
    uniqueConstraints = [UniqueConstraint(name = "uk_rider_verification_attempts_user", columnNames = ["user_id"])],
    indexes = [Index(name = "idx_rider_verification_attempts_locked_until", columnList = "locked_until")],
)
class RiderVerificationAttempt(
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    val user: User,
) : BaseEntity() {

    @field:Column(name = "failed_attempt_count", nullable = false)
    final var failedAttemptCount: Int = 0
        private set

    @field:Column(name = "window_started_at")
    final var windowStartedAt: Instant? = null
        private set

    @field:Column(name = "locked_until")
    final var lockedUntil: Instant? = null
        private set

    fun isLockedAt(now: Instant): Boolean = lockedUntil?.let(now::isBefore) == true

    fun registerFailure(now: Instant): Instant? {
        if (lockedUntil?.let { !now.isBefore(it) } == true) clear()
        if (failedAttemptCount == 0) windowStartedAt = now
        failedAttemptCount += 1
        if (failedAttemptCount >= MAX_FAILURES) {
            lockedUntil = now.plus(LOCK_DURATION)
        }
        return lockedUntil
    }

    fun clear() {
        failedAttemptCount = 0
        windowStartedAt = null
        lockedUntil = null
    }

    private companion object {
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
