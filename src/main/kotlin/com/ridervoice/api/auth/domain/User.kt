package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User : BaseEntity() {

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 32)
    final var status: UserStatus = UserStatus.PENDING_TERMS
        private set

    @field:Column(name = "terms_version", length = 50)
    final var termsVersion: String? = null
        private set

    @field:Column(name = "terms_agreed_at")
    final var termsAgreedAt: Instant? = null
        private set

    fun agreeToTerms(termsVersion: String, agreedAt: Instant) {
        require(termsVersion.isNotBlank()) { "Terms version must not be blank" }
        check(status == UserStatus.PENDING_TERMS) { "Only a user pending terms can agree" }

        this.termsVersion = termsVersion
        termsAgreedAt = agreedAt
        status = UserStatus.ACTIVE
    }
}
