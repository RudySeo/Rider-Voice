package com.ridervoice.api.moderation.application.model

import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason
import java.time.Instant

data class ReviewReportResult(
    val reportId: Long,
    val reviewId: Long,
    val reason: ReviewReportReason,
    val status: ReportStatus,
    val decision: ReviewReportDecision?,
    val createdAt: Instant,
    val decidedAt: Instant?,
)

data class RestaurantInfoReportResult(
    val reportId: Long,
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val status: ReportStatus,
    val decision: RestaurantInfoReportDecision?,
    val createdAt: Instant,
    val decidedAt: Instant?,
)

data class ReportModerationCursor(
    val createdAt: Instant,
    val reportId: Long,
) {
    init {
        require(reportId > 0) { "Report ID must be positive" }
    }
}

data class PendingReviewReportResult(
    val reportId: Long,
    val reporterUserId: Long,
    val reviewId: Long,
    val reason: ReviewReportReason,
    val details: String?,
    val createdAt: Instant,
)

data class PendingReviewReportPageResult(
    val items: List<PendingReviewReportResult>,
    val nextCursor: ReportModerationCursor?,
)

data class PendingRestaurantInfoReportResult(
    val reportId: Long,
    val reporterUserId: Long,
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val details: String?,
    val createdAt: Instant,
)

data class PendingRestaurantInfoReportPageResult(
    val items: List<PendingRestaurantInfoReportResult>,
    val nextCursor: ReportModerationCursor?,
)
