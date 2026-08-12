package com.ridervoice.api.auth.application.port.`in`

import com.ridervoice.api.auth.application.AuthTokens
import com.ridervoice.api.auth.application.UserSummary

fun interface RefreshSessionUseCase {
    fun refresh(command: RefreshSessionCommand): AuthTokens
}

fun interface LogoutUseCase {
    fun logout(command: LogoutCommand)
}

fun interface GetCurrentUserUseCase {
    fun get(query: GetCurrentUserQuery): UserSummary
}

data class RefreshSessionCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank" }
    }
}

data class LogoutCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank" }
    }
}

data class GetCurrentUserQuery(
    val userId: Long,
) {
    init {
        require(userId > 0) { "User ID must be positive" }
    }
}
