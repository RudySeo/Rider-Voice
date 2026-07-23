package com.ridervoice.api.common.security

import java.util.UUID

sealed interface BearerPrincipal {
    val userId: UUID
    val authority: String
}

data class AuthenticatedUserPrincipal(override val userId: UUID) : BearerPrincipal {
    override val authority: String
        get() = AUTHORITY

    companion object {
        const val AUTHORITY = "ROLE_USER"
    }
}

data class OnboardingPrincipal(override val userId: UUID) : BearerPrincipal {
    override val authority: String
        get() = AUTHORITY

    companion object {
        const val AUTHORITY = "ROLE_ONBOARDING"
    }
}

fun interface AccessTokenAuthenticator {
    fun authenticate(accessToken: String): BearerPrincipal?
}
