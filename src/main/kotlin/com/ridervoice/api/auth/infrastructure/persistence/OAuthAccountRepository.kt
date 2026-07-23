package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.OAuthAccount
import com.ridervoice.api.auth.domain.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthAccountRepository : JpaRepository<OAuthAccount, Long> {
    fun findByProviderAndProviderSubject(provider: OAuthProvider, providerSubject: String): Optional<OAuthAccount>
}
