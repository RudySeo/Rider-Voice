package com.ridervoice.api.review.domain

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.restaurant.domain.Restaurant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "author_restaurant_review_states",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_author_restaurant_review_states_author_restaurant",
            columnNames = ["author_user_id", "restaurant_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_author_restaurant_review_states_current_review",
            columnList = "current_review_id",
        ),
    ],
)
class AuthorRestaurantReviewState(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "author_user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_review_states_author_user"),
    )
    val author: User,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "restaurant_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_review_states_restaurant"),
    )
    val restaurant: Restaurant,
    lastSubmittedAt: Instant,
    lastSequence: Long,
    currentReview: Review?,
) : BaseEntity() {

    @field:Column(name = "last_submitted_at", nullable = false)
    final var lastSubmittedAt: Instant = lastSubmittedAt
        private set

    @field:Column(name = "last_sequence", nullable = false)
    final var lastSequence: Long = lastSequence
        private set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(
        name = "current_review_id",
        foreignKey = ForeignKey(name = "fk_review_states_current_review"),
    )
    final var currentReview: Review? = currentReview
        private set

    init {
        require(lastSequence > 0) { "Last review sequence must be positive" }
    }

    fun synchronize(lastSubmittedAt: Instant, lastSequence: Long, currentReview: Review?) {
        require(lastSequence > 0) { "Last review sequence must be positive" }
        this.lastSubmittedAt = lastSubmittedAt
        this.lastSequence = lastSequence
        this.currentReview = currentReview
    }
}
