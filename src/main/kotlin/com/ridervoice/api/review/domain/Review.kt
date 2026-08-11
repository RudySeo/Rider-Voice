package com.ridervoice.api.review.domain

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.common.persistence.BaseEntity
import com.ridervoice.api.restaurant.domain.Restaurant
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "reviews",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_reviews_author_restaurant_current_slot",
            columnNames = ["author_user_id", "restaurant_id", "current_slot"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_reviews_author_restaurant_created",
            columnList = "author_user_id, restaurant_id, created_at, id",
        ),
        Index(
            name = "idx_reviews_restaurant_visibility_created",
            columnList = "restaurant_id, current_slot, visibility_status, deleted_at, created_at, id",
        ),
    ],
)
class Review(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "author_user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_reviews_author_user"),
    )
    val author: User,
    restaurant: Restaurant,
    @field:Convert(converter = VisitMonthAttributeConverter::class)
    @field:Column(name = "visit_month", nullable = false, updatable = false, length = 7)
    val visitMonth: VisitMonth,
    ratings: ReviewRatings,
    comment: String?,
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "restaurant_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_reviews_restaurant"),
    )
    final var restaurant: Restaurant = restaurant
        private set

    @field:Embedded
    final var ratings: ReviewRatings = ratings
        private set

    @field:Column(length = ReviewCommentPolicy.MAX_LENGTH)
    final var comment: String? = ReviewCommentPolicy.normalize(comment)
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "comment_moderation_status", nullable = false, length = 32)
    final var commentModerationStatus: ReviewCommentStatus = initialCommentStatus(this.comment)
        private set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "visibility_status", nullable = false, length = 20)
    final var visibilityStatus: ReviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE
        private set

    @field:Column(name = "deleted_at")
    final var deletedAt: Instant? = null
        private set

    @field:Column(name = "current_slot")
    final var currentSlot: Int? = ACTIVE_SLOT
        private set

    val isActive: Boolean
        get() = currentSlot == ACTIVE_SLOT && deletedAt == null && visibilityStatus == ReviewVisibilityStatus.ACTIVE

    fun update(ratings: ReviewRatings, comment: String?) {
        val normalizedComment = ReviewCommentPolicy.normalize(comment)
        this.ratings = ratings
        if (this.comment != normalizedComment) {
            val wasHiddenByReport = commentModerationStatus == ReviewCommentStatus.HIDDEN_REPORTED
            this.comment = normalizedComment
            commentModerationStatus = when {
                this.comment == null -> ReviewCommentStatus.NONE
                wasHiddenByReport -> ReviewCommentStatus.HIDDEN_REPORTED
                else -> ReviewCommentStatus.PUBLISHED
            }
        }
    }

    fun hidePublishedCommentForReport() {
        check(commentModerationStatus == ReviewCommentStatus.PUBLISHED) {
            "Only a published comment can be hidden by a report"
        }
        commentModerationStatus = ReviewCommentStatus.HIDDEN_REPORTED
    }

    fun restoreReportedComment() {
        check(commentModerationStatus == ReviewCommentStatus.HIDDEN_REPORTED) {
            "Only a reported comment can be restored"
        }
        commentModerationStatus = ReviewCommentStatus.PUBLISHED
    }

    fun permanentlyHideReportedComment() {
        check(commentModerationStatus == ReviewCommentStatus.HIDDEN_REPORTED) {
            "Only a reported comment can be permanently hidden"
        }
        commentModerationStatus = ReviewCommentStatus.REJECTED
    }

    fun exclude() {
        check(isActive) {
            "Only an active review can be excluded"
        }
        visibilityStatus = ReviewVisibilityStatus.EXCLUDED
        currentSlot = null
    }

    fun softDelete(deletedAt: Instant) {
        check(isActive) { "Only an active review can be deleted" }
        this.deletedAt = deletedAt
        currentSlot = null
    }

    fun supersede() {
        check(isActive) { "Only an active review can be superseded" }
        currentSlot = null
    }

    fun relinkToRestaurant(restaurant: Restaurant) {
        this.restaurant = restaurant
    }

    private companion object {
        const val ACTIVE_SLOT = 1

        fun initialCommentStatus(comment: String?): ReviewCommentStatus =
            if (comment == null) ReviewCommentStatus.NONE else ReviewCommentStatus.PUBLISHED
    }
}
