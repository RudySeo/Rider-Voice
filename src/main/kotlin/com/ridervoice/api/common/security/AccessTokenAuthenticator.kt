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
        require(authority == USER_AUTHORITY || authority == RIDER_AUTHORITY || authority == ADMIN_AUTHORITY) {
            "Unsupported authenticated user authority"
        }
    }

    companion object {
        const val USER_AUTHORITY = "ROLE_USER"
        const val RIDER_AUTHORITY = "ROLE_RIDER"
        const val ADMIN_AUTHORITY = "ROLE_ADMIN"
    }
}

fun interface AccessTokenAuthenticator {
    fun authenticate(accessToken: String): BearerPrincipal?
}
