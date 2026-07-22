package com.ridervoice.api.common.security

import java.util.UUID

data class AuthenticatedUserPrincipal(val userId: UUID)

fun interface AccessTokenAuthenticator {
    fun authenticate(accessToken: String): AuthenticatedUserPrincipal?
}
