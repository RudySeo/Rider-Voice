package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.moderation.application.model.PendingRestaurantInfoReportPageResult
import com.ridervoice.api.moderation.application.model.PendingReviewReportPageResult
import com.ridervoice.api.moderation.application.model.ReportModerationCursor
import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.moderation.application.model.ReviewReportResult
import com.ridervoice.api.moderation.application.port.`in`.CreateRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.CreateReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideRestaurantInfoReportCommand
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewReportCommand
import com.ridervoice.api.moderation.application.port.`in`.ListPendingRestaurantInfoReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewReportsQuery
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCorrection
import com.ridervoice.api.moderation.application.port.`in`.RelinkExistingPickupCorrection
import com.ridervoice.api.moderation.application.port.`in`.RelinkVerifiedAddressCorrection
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantCorrection
import com.ridervoice.api.moderation.application.port.`in`.CloseRestaurantCorrection
import com.ridervoice.api.moderation.presentation.dto.CreateRestaurantInfoReportRequest
import com.ridervoice.api.moderation.presentation.dto.CreateReviewReportRequest
import com.ridervoice.api.moderation.presentation.dto.ModerationPageRequest
import com.ridervoice.api.moderation.presentation.dto.PendingRestaurantInfoReportPageResponse
import com.ridervoice.api.moderation.presentation.dto.PendingRestaurantInfoReportResponse
import com.ridervoice.api.moderation.presentation.dto.PendingReviewReportPageResponse
import com.ridervoice.api.moderation.presentation.dto.PendingReviewReportResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantInfoReportDecisionRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantInfoReportResponse
import com.ridervoice.api.moderation.presentation.dto.ReviewReportDecisionRequest
import com.ridervoice.api.moderation.presentation.dto.ReviewReportResponse
import com.ridervoice.api.moderation.presentation.dto.*
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

@Component
class ModerationHttpMapper {
    fun toCreateReviewReportCommand(
        reporterUserId: Long,
        reviewId: Long,
        request: CreateReviewReportRequest,
    ) = CreateReviewReportCommand(reporterUserId, reviewId, requireNotNull(request.reason), request.details)

    fun toCreateRestaurantReportCommand(
        reporterUserId: Long,
        restaurantId: Long,
        request: CreateRestaurantInfoReportRequest,
    ) = CreateRestaurantInfoReportCommand(
        reporterUserId,
        restaurantId,
        requireNotNull(request.reason),
        request.details,
    )

    fun toReviewReportQuery(adminUserId: Long, request: ModerationPageRequest) = ListPendingReviewReportsQuery(
        adminUserId,
        request.cursor?.let(::decodeReportCursor),
        request.size,
    )

    fun toRestaurantReportQuery(
        adminUserId: Long,
        request: ModerationPageRequest,
    ) = ListPendingRestaurantInfoReportsQuery(
        adminUserId,
        request.cursor?.let(::decodeReportCursor),
        request.size,
    )

    fun toReviewReportDecisionCommand(
        adminUserId: Long,
        reportId: Long,
        request: ReviewReportDecisionRequest,
    ) = DecideReviewReportCommand(adminUserId, reportId, requireNotNull(request.decision), request.reason)

    fun toRestaurantReportDecisionCommand(
        adminUserId: Long,
        reportId: Long,
        request: RestaurantInfoReportDecisionRequest,
    ) = DecideRestaurantInfoReportCommand(
        adminUserId,
        reportId,
        requireNotNull(request.decision),
        request.reason,
        request.correction?.let {
            when (it) {
                is RenameRestaurantCorrectionRequest -> RenameRestaurantCorrection(it.name)
                is RelinkExistingPickupCorrectionRequest -> RelinkExistingPickupCorrection(it.pickupLocationId)
                is RelinkVerifiedAddressCorrectionRequest -> RelinkVerifiedAddressCorrection(
                    it.addressQuery,
                    it.selectedStandardAddress,
                    it.detailAddress,
                )
                is MergeRestaurantCorrectionRequest -> MergeRestaurantCorrection(it.canonicalRestaurantId)
                is CloseRestaurantCorrectionRequest -> CloseRestaurantCorrection
            }
        },
    )

    fun toResponse(result: ReviewReportResult) = ReviewReportResponse(
        result.reportId,
        result.reviewId,
        result.reason,
        result.status,
        result.decision,
        result.createdAt,
        result.decidedAt,
    )

    fun toResponse(result: RestaurantInfoReportResult) = RestaurantInfoReportResponse(
        result.reportId,
        result.restaurantId,
        result.reason,
        result.status,
        result.decision,
        result.createdAt,
        result.decidedAt,
    )

    fun toResponse(result: PendingReviewReportPageResult) = PendingReviewReportPageResponse(
        items = result.items.map {
            PendingReviewReportResponse(
                it.reportId,
                it.reporterUserId,
                it.reviewId,
                it.reason,
                it.details,
                it.createdAt,
            )
        },
        nextCursor = result.nextCursor?.let { encodeCursor(it.createdAt, it.reportId) },
    )

    fun toResponse(result: PendingRestaurantInfoReportPageResult) = PendingRestaurantInfoReportPageResponse(
        items = result.items.map {
            PendingRestaurantInfoReportResponse(
                it.reportId,
                it.reporterUserId,
                it.restaurantId,
                it.reason,
                it.details,
                it.createdAt,
            )
        },
        nextCursor = result.nextCursor?.let { encodeCursor(it.createdAt, it.reportId) },
    )

    private fun decodeReportCursor(value: String): ReportModerationCursor {
        val (createdAt, id) = decodeCursor(value)
        return ReportModerationCursor(createdAt, id)
    }

    private fun encodeCursor(createdAt: Instant, id: Long): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString("$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(value: String): Pair<Instant, Long> = try {
        val decoded = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        val parts = decoded.split('|')
        require(parts.size == 2)
        val createdAt = Instant.parse(parts[0])
        val id = parts[1].toLong()
        require(id > 0)
        createdAt to id
    } catch (exception: Exception) {
        throw IllegalArgumentException("Invalid moderation cursor", exception)
    }
}
