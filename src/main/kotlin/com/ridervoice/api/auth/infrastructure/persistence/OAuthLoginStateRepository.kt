package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OAuthLoginState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthLoginStateRepository : JpaRepository<OAuthLoginState, Long> {
    fun findByStateHash(stateHash: String): Optional<OAuthLoginState>
}
