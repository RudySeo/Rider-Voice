package com.ridervoice.api.common.security

sealed interface BearerPrincipal {
    val userId: Long
    val authority: String
}

data class AuthenticatedUserPrincipal(override val userId: Long) : BearerPrincipal {
    override val authority: String
        get() = AUTHORITY

    companion object {
        const val AUTHORITY = "ROLE_USER"
    }
}

data class OnboardingPrincipal(
    override val userId: Long,
    val tokenHash: String = "",
) : BearerPrincipal {
    override val authority: String
        get() = AUTHORITY

    companion object {
        const val AUTHORITY = "ROLE_ONBOARDING"
    }
}

fun interface AccessTokenAuthenticator {
    fun authenticate(accessToken: String): BearerPrincipal?
}
