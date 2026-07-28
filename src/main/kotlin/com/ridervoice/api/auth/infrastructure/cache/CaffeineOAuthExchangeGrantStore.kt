package com.ridervoice.api.auth.infrastructure.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrant
import com.ridervoice.api.auth.application.port.out.OAuthExchangeGrantStore
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CaffeineOAuthExchangeGrantStore : OAuthExchangeGrantStore {
    private val grants = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(EXPIRY_SECONDS))
        .maximumSize(MAXIMUM_GRANTS)
        .build<String, OAuthExchangeGrant>()

    override fun save(codeHash: String, grant: OAuthExchangeGrant) {
        grants.put(codeHash, grant)
    }

    override fun consume(codeHash: String, consumedAt: Instant): OAuthExchangeGrant? {
        var consumed: OAuthExchangeGrant? = null
        grants.asMap().computeIfPresent(codeHash) { _, grant ->
            if (consumedAt.isBefore(grant.expiresAt)) {
                consumed = grant
            }
            null
        }
        return consumed
    }

    private companion object {
        const val EXPIRY_SECONDS = 60L
        const val MAXIMUM_GRANTS = 10_000L
    }
}
