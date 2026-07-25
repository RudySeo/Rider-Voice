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

@Entity
@Table(
    name = "reviews",
    indexes = [
        Index(
            name = "idx_reviews_author_restaurant_sequence",
            columnList = "author_user_id, restaurant_id, submission_sequence",
        ),
        Index(
            name = "idx_reviews_restaurant_visibility_created",
            columnList = "restaurant_id, visibility_status, created_at, id",
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
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "restaurant_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_reviews_restaurant"),
    )
    val restaurant: Restaurant,
    @field:Convert(converter = VisitMonthAttributeConverter::class)
    @field:Column(name = "visit_month", nullable = false, updatable = false, length = 7)
    val visitMonth: VisitMonth,
    ratings: ReviewRatings,
    comment: String?,
    @field:Column(name = "submission_sequence", nullable = false, updatable = false)
    val sequence: Long,
) : BaseEntity() {

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

    init {
        require(sequence > 0) { "Review sequence must be positive" }
    }

    fun update(ratings: ReviewRatings, comment: String?) {
        val normalizedComment = ReviewCommentPolicy.normalize(comment)
        this.ratings = ratings
        if (this.comment != normalizedComment) {
            this.comment = normalizedComment
            commentModerationStatus = initialCommentStatus(this.comment)
        }
    }

    fun publishComment() {
        check(commentModerationStatus == ReviewCommentStatus.PENDING) {
            "Only a pending comment can be published"
        }
        commentModerationStatus = ReviewCommentStatus.PUBLISHED
    }

    fun rejectComment() {
        check(commentModerationStatus == ReviewCommentStatus.PENDING) {
            "Only a pending comment can be rejected"
        }
        commentModerationStatus = ReviewCommentStatus.REJECTED
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
        check(visibilityStatus == ReviewVisibilityStatus.ACTIVE) {
            "Only an active review can be excluded"
        }
        visibilityStatus = ReviewVisibilityStatus.EXCLUDED
    }

    private companion object {
        fun initialCommentStatus(comment: String?): ReviewCommentStatus =
            if (comment == null) ReviewCommentStatus.NONE else ReviewCommentStatus.PENDING
    }
}
