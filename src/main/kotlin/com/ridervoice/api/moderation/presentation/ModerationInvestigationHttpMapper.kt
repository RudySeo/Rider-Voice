package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.model.AdminRestaurantDetailResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchPageResult
import com.ridervoice.api.moderation.application.model.AdminReviewDetailResult
import com.ridervoice.api.moderation.application.model.ModerationAuditCursor
import com.ridervoice.api.moderation.application.model.ModerationAuditPageResult
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsQuery
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsQuery
import com.ridervoice.api.moderation.presentation.dto.*
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

@Component
class ModerationInvestigationHttpMapper {
    fun toSearchQuery(adminUserId: Long, request: AdminRestaurantSearchRequest) = SearchAdminRestaurantsQuery(
        adminUserId,
        request.query,
        request.status,
        request.cursor?.let { decodeCursor(it).let { value -> AdminRestaurantCursor(value.first, value.second) } },
        request.size,
    )

    fun toAuditQuery(adminUserId: Long, request: ModerationAuditSearchRequest) = ListModerationAuditsQuery(
        adminUserId,
        request.targetType,
        request.targetId,
        request.actorUserId,
        request.action,
        request.cursor?.let { decodeCursor(it).let { value -> ModerationAuditCursor(value.first, value.second) } },
        request.size,
    )

    fun toResponse(result: AdminReviewDetailResult) = AdminReviewDetailResponse(
        result.reviewId,
        AdminReviewAuthorResponse(result.authorUserId, result.authorStatus, result.authorActivityMonths, result.authorPublicReviewCount),
        AdminReviewRestaurantResponse(result.restaurantId, result.restaurantName, result.restaurantStatus, result.pickupLocationId, result.pickupAddress),
        result.visitMonth.toString(),
        result.ratings.let { AdminReviewRatingsResponse(it.pickupSpaceCleanliness, it.packagingStability, it.orderReadiness, it.handoffAccuracy, it.staffInteraction, it.riderRespect) },
        result.comment,
        result.commentStatus,
        result.visibilityStatus,
        result.active,
        result.deletedAt,
        result.createdAt,
        result.updatedAt,
    )

    fun toResponse(result: AdminRestaurantSearchPageResult) = AdminRestaurantSearchPageResponse(
        result.items.map { AdminRestaurantSearchItemResponse(it.restaurantId, it.name, it.status, it.pickupLocationId, it.standardAddress, it.detailAddress, it.createdAt) },
        result.nextCursor?.let { encodeCursor(it.createdAt, it.restaurantId) },
    )

    fun toResponse(result: AdminRestaurantDetailResult) = AdminRestaurantDetailResponse(
        result.restaurantId,
        result.name,
        result.status,
        AdminPickupLocationResponse(result.pickupLocationId, result.standardAddress, result.detailAddress, result.latitude, result.longitude),
        result.kakaoPlaceId,
        result.platforms,
        result.pendingReportCount,
        result.createdAt,
        result.updatedAt,
    )

    fun toResponse(result: ModerationAuditPageResult) = ModerationAuditPageResponse(
        result.items.map { ModerationAuditResponse(it.auditId, it.actorUserId, it.action, it.targetType, it.targetId, it.reason, it.beforeState, it.afterState, it.occurredAt, it.createdAt) },
        result.nextCursor?.let { encodeCursor(it.createdAt, it.auditId) },
    )

    private fun encodeCursor(createdAt: Instant, id: Long): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(value: String): Pair<Instant, Long> = try {
        val parts = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split('|')
        require(parts.size == 2)
        Instant.parse(parts[0]) to parts[1].toLong().also { require(it > 0) }
    } catch (exception: Exception) {
        throw IllegalArgumentException("Invalid moderation investigation cursor", exception)
    }
}
