package com.ridervoice.api.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    @field:CreatedDate
    @field:Column(nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @field:LastModifiedDate
    @field:Column(nullable = false)
    lateinit var updatedAt: Instant
        protected set
}
