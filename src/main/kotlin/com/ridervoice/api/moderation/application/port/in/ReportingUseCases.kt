package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.ReviewReportResult
import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportPageResult
import com.ridervoice.api.moderation.application.model.ReportModerationCursor
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

fun interface ListPendingReviewReportsUseCase {
    fun list(query: ListPendingReviewReportsQuery): PendingReviewReportPageResult
}

data class ListPendingReviewReportsQuery(
    val adminUserId: Long,
    val cursor: ReportModerationCursor?,
    val size: Int,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(size in 1..50) { "Review report page size must be between 1 and 50" }
    }
}

fun interface ListPendingRestaurantInfoReportsUseCase {
    fun list(query: ListPendingRestaurantInfoReportsQuery): PendingRestaurantInfoReportPageResult
}

data class ListPendingRestaurantInfoReportsQuery(
    val adminUserId: Long,
    val cursor: ReportModerationCursor?,
    val size: Int,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(size in 1..50) { "Restaurant report page size must be between 1 and 50" }
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
    val correction: RestaurantInfoCorrectionCommand? = null,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(reportId > 0) { "Restaurant information report ID must be positive" }
        require(
            (decision == RestaurantInfoReportDecision.DISMISS && correction == null) ||
                (decision == RestaurantInfoReportDecision.RESOLVE && correction != null),
        ) { "DISMISS forbids correction and RESOLVE requires correction" }
    }
}

sealed interface RestaurantInfoCorrectionCommand
data class RenameRestaurantCorrection(val name: String) : RestaurantInfoCorrectionCommand
data class RelinkExistingPickupCorrection(val pickupLocationId: Long) : RestaurantInfoCorrectionCommand
data class RelinkVerifiedAddressCorrection(
    val addressQuery: String,
    val selectedStandardAddress: String,
    val detailAddress: String?,
) : RestaurantInfoCorrectionCommand
data class MergeRestaurantCorrection(val canonicalRestaurantId: Long) : RestaurantInfoCorrectionCommand
data object CloseRestaurantCorrection : RestaurantInfoCorrectionCommand
