package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.moderation.application.model.AdminExternalReferenceResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.model.AdminRestaurantDetailResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchItemResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchPageResult
import com.ridervoice.api.moderation.application.model.AdminReviewDetailResult
import com.ridervoice.api.moderation.application.model.ModerationAuditCursor
import com.ridervoice.api.moderation.application.model.ModerationAuditPageResult
import com.ridervoice.api.moderation.application.model.ModerationAuditResult
import com.ridervoice.api.moderation.application.port.`in`.GetAdminRestaurantDetailQuery
import com.ridervoice.api.moderation.application.port.`in`.GetAdminRestaurantDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailQuery
import com.ridervoice.api.moderation.application.port.`in`.GetAdminReviewDetailUseCase
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsQuery
import com.ridervoice.api.moderation.application.port.`in`.ListModerationAuditsUseCase
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsQuery
import com.ridervoice.api.moderation.application.port.`in`.SearchAdminRestaurantsUseCase
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationInvestigationQuery
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurantDetail
import com.ridervoice.api.moderation.application.port.out.StoredAdminReviewDetail
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
internal class ModerationInvestigationService(
    private val admins: ModerationAdminRepository,
    private val investigation: ModerationInvestigationQuery,
    private val clock: Clock,
) : GetAdminReviewDetailUseCase, SearchAdminRestaurantsUseCase,
    GetAdminRestaurantDetailUseCase, ListModerationAuditsUseCase {

    @Transactional(readOnly = true)
    override fun get(query: GetAdminReviewDetailQuery): AdminReviewDetailResult {
        requireAdmin(query.adminUserId)
        return investigation.findReview(query.reviewId)?.toResult()
            ?: throw ResourceNotFoundException("Review was not found")
    }

    @Transactional(readOnly = true)
    override fun search(query: SearchAdminRestaurantsQuery): AdminRestaurantSearchPageResult {
        requireAdmin(query.adminUserId)
        require(query.size in 1..50) { "Restaurant search page size must be between 1 and 50" }
        val normalized = RestaurantNormalization.normalizedText(query.query)
        require(normalized.length in 2..100) { "Restaurant search query must be between 2 and 100 characters" }
        val page = investigation.searchRestaurants(normalized, query.status, query.cursor, query.size + 1)
        val items = page.take(query.size)
        return AdminRestaurantSearchPageResult(
            items = items.map { it.toSearchItem() },
            nextCursor = items.lastOrNull()
                ?.takeIf { page.size > query.size }
                ?.let { AdminRestaurantCursor(it.createdAt, it.restaurantId) },
        )
    }

    @Transactional(readOnly = true)
    override fun get(query: GetAdminRestaurantDetailQuery): AdminRestaurantDetailResult {
        requireAdmin(query.adminUserId)
        return investigation.findRestaurant(query.restaurantId)?.toDetail()
            ?: throw ResourceNotFoundException("Restaurant was not found")
    }

    @Transactional(readOnly = true)
    override fun list(query: ListModerationAuditsQuery): ModerationAuditPageResult {
        requireAdmin(query.adminUserId)
        require(query.size in 1..50) { "Audit page size must be between 1 and 50" }
        require(query.targetId == null || query.targetId > 0) { "Audit target ID must be positive" }
        require(query.actorUserId == null || query.actorUserId > 0) { "Audit actor user ID must be positive" }
        val page = investigation.findAudits(
            query.targetType,
            query.targetId,
            query.actorUserId,
            query.action,
            query.cursor,
            query.size + 1,
        )
        val items = page.take(query.size)
        return ModerationAuditPageResult(
            items = items.map {
                ModerationAuditResult(
                    it.auditId,
                    it.actorUserId,
                    it.action,
                    it.targetType,
                    it.targetId,
                    it.reason,
                    it.beforeState,
                    it.afterState,
                    it.occurredAt,
                    it.createdAt,
                )
            },
            nextCursor = items.lastOrNull()
                ?.takeIf { page.size > query.size }
                ?.let { ModerationAuditCursor(it.createdAt, it.auditId) },
        )
    }

    private fun StoredAdminReviewDetail.toResult() = AdminReviewDetailResult(
        reviewId,
        authorUserId,
        authorStatus,
        activityMonths(firstPublicReviewAt),
        publicReviewCount,
        restaurantId,
        restaurantName,
        restaurantStatus,
        pickupLocationId,
        pickupAddress,
        visitMonth,
        ratings,
        comment,
        commentStatus,
        visibilityStatus,
        historyStatus,
        sequence,
        createdAt,
        updatedAt,
    )

    private fun StoredAdminRestaurantDetail.toSearchItem() = AdminRestaurantSearchItemResult(
        restaurantId,
        name,
        status,
        canonicalRestaurantId,
        pickupLocationId,
        standardAddress,
        detailAddress,
        createdAt,
    )

    private fun StoredAdminRestaurantDetail.toDetail() = AdminRestaurantDetailResult(
        restaurantId,
        name,
        normalizedName,
        status,
        canonicalRestaurantId,
        pickupLocationId,
        standardAddress,
        detailAddress,
        latitude,
        longitude,
        externalReferences.map { AdminExternalReferenceResult(it.provider, it.externalPlaceId) },
        platforms,
        pendingReportCount,
        createdAt,
        updatedAt,
    )

    private fun activityMonths(firstPublicReviewAt: java.time.Instant): Int {
        val first = YearMonth.from(firstPublicReviewAt.atZone(ZoneOffset.UTC))
        val current = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC))
        return (ChronoUnit.MONTHS.between(first, current) + 1).coerceAtLeast(1).toInt()
    }

    private fun requireAdmin(userId: Long) {
        if (!admins.isActiveAdmin(userId)) {
            throw ApiException(ApiErrorCode.ACCESS_DENIED, "Active administrator role is required")
        }
    }
}
