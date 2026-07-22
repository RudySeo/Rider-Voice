package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "oauth_accounts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_oauth_accounts_provider_subject",
            columnNames = ["provider", "provider_subject"],
        ),
        UniqueConstraint(
            name = "uk_oauth_accounts_user_provider",
            columnNames = ["user_id", "provider"],
        ),
    ],
)
class OAuthAccount(
    @field:Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, updatable = false, length = 20)
    val provider: OAuthProvider,
    @field:Column(name = "provider_subject", nullable = false, updatable = false, length = 255)
    val providerSubject: String,
    @field:Id
    @field:Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    init {
        require(providerSubject.isNotBlank()) { "Provider subject must not be blank" }
    }
}
