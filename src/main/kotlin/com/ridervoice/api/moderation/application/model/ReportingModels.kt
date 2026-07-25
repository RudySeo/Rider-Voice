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
