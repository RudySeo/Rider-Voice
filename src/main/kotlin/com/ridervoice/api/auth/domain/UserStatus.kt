package com.ridervoice.api.auth.domain

enum class UserStatus {
    PENDING_TERMS,
    ACTIVE,
    RATE_LIMITED,
    SUSPENDED,
    WITHDRAWN,
}
