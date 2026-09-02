package com.ridervoice.api.auth.domain

import com.ridervoice.api.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "users")
class User : BaseEntity() {

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    @field:ColumnDefault("'USER'")
    final var role: UserRole = UserRole.USER
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 32)
    final var status: UserStatus = UserStatus.ACTIVE
        private set

    fun promoteToRider(): Boolean {
        if (role != UserRole.USER) return false
        role = UserRole.RIDER
        return true
    }
}
