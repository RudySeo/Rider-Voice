package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OAuthLoginState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OAuthLoginStateRepository : JpaRepository<OAuthLoginState, UUID> {
    fun findByStateHash(stateHash: String): Optional<OAuthLoginState>
}
