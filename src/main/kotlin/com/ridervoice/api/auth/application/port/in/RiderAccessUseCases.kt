package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.application.UserSummary

fun interface VerifyRiderUseCase {
    fun verify(command: VerifyRiderCommand): UserSummary
}

fun interface RotateRiderInviteCodeUseCase {
    fun rotate(command: RotateRiderInviteCodeCommand)
}

fun interface EnsureReviewWriterUseCase {
    fun ensureEligible(userId: Long)
}

data class VerifyRiderCommand(val userId: Long, val code: String) {
    init {
        require(userId > 0)
        require(CODE_PATTERN.matches(code))
    }
}

data class RotateRiderInviteCodeCommand(val adminUserId: Long, val code: String) {
    init {
        require(adminUserId > 0)
        require(CODE_PATTERN.matches(code))
    }
}

private val CODE_PATTERN = Regex("^[0-9]{6}$")
