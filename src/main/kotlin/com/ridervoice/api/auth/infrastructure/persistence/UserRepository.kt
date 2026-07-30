package com.ridervoice.api.auth.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
