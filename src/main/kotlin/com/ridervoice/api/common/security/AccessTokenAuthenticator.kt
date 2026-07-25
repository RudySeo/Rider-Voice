package com.ridervoice.api.common.security

sealed interface BearerPrincipal {
    val userId: Long
    val authority: String
}

data class AuthenticatedUserPrincipal(
    override val userId: Long,
    override val authority: String = USER_AUTHORITY,
) : BearerPrincipal {

    init {
        require(authority == USER_AUTHORITY || authority == ADMIN_AUTHORITY) {
            "Unsupported authenticated user authority"
        }
    }

    companion object {
        const val USER_AUTHORITY = "ROLE_USER"
        const val ADMIN_AUTHORITY = "ROLE_ADMIN"
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
