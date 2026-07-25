package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.ReviewReportResult
import com.ridervoice.api.moderation.domain.RestaurantInfoReportDecision
import com.ridervoice.api.moderation.domain.RestaurantInfoReportReason
import com.ridervoice.api.moderation.domain.ReviewReportDecision
import com.ridervoice.api.moderation.domain.ReviewReportReason

fun interface CreateReviewReportUseCase {
    fun createReviewReport(command: CreateReviewReportCommand): ReviewReportResult
}

data class CreateReviewReportCommand(
    val reporterUserId: Long,
    val reviewId: Long,
    val reason: ReviewReportReason,
    val details: String?,
) {
    init {
        require(reporterUserId > 0) { "Reporter user ID must be positive" }
        require(reviewId > 0) { "Review ID must be positive" }
    }
}

fun interface CreateRestaurantInfoReportUseCase {
    fun createRestaurantInfoReport(command: CreateRestaurantInfoReportCommand): RestaurantInfoReportResult
}

data class CreateRestaurantInfoReportCommand(
    val reporterUserId: Long,
    val restaurantId: Long,
    val reason: RestaurantInfoReportReason,
    val details: String?,
) {
    init {
        require(reporterUserId > 0) { "Reporter user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}

fun interface DecideReviewReportUseCase {
    fun decideReviewReport(command: DecideReviewReportCommand): ReviewReportResult
}

data class DecideReviewReportCommand(
    val adminUserId: Long,
    val reportId: Long,
    val decision: ReviewReportDecision,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(reportId > 0) { "Review report ID must be positive" }
    }
}

fun interface DecideRestaurantInfoReportUseCase {
    fun decideRestaurantInfoReport(command: DecideRestaurantInfoReportCommand): RestaurantInfoReportResult
}

data class DecideRestaurantInfoReportCommand(
    val adminUserId: Long,
    val reportId: Long,
    val decision: RestaurantInfoReportDecision,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(reportId > 0) { "Restaurant information report ID must be positive" }
    }
}
