package com.ridervoice.api.review.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

enum class ReviewRating {
    VERY_GOOD,
    GOOD,
    NEEDS_IMPROVEMENT,
    MAJOR_IMPROVEMENT,
    NOT_OBSERVED,
}

enum class ReviewCommentStatus {
    NONE,
    PENDING,
    PUBLISHED,
    REJECTED,
    HIDDEN_REPORTED,
}

enum class ReviewVisibilityStatus {
    ACTIVE,
    EXCLUDED,
}

@Embeddable
data class ReviewRatings(
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "pickup_space_cleanliness", nullable = false, length = 32)
    val pickupSpaceCleanliness: ReviewRating,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "packaging_stability", nullable = false, length = 32)
    val packagingStability: ReviewRating,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "order_readiness", nullable = false, length = 32)
    val orderReadiness: ReviewRating,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "handoff_accuracy", nullable = false, length = 32)
    val handoffAccuracy: ReviewRating,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "staff_interaction", nullable = false, length = 32)
    val staffInteraction: ReviewRating,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "rider_respect", nullable = false, length = 32)
    val riderRespect: ReviewRating,
)
