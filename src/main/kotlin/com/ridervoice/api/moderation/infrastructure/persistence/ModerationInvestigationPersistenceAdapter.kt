package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.model.ModerationAuditCursor
import com.ridervoice.api.moderation.application.port.out.ModerationInvestigationQuery
import com.ridervoice.api.moderation.application.port.out.StoredAdminExternalReference
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurantDetail
import com.ridervoice.api.moderation.application.port.out.StoredAdminReviewDetail
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
internal class ModerationInvestigationPersistenceAdapter(
    private val entityManager: EntityManager,
) : ModerationInvestigationQuery {

    override fun findReview(reviewId: Long): StoredAdminReviewDetail? {
        val review = entityManager.createQuery(
            """
            select review
            from Review review
            join fetch review.author
            join fetch review.restaurant restaurant
            join fetch restaurant.pickupLocation
            where review.id = :reviewId
            """.trimIndent(),
            Review::class.java,
        ).setParameter("reviewId", reviewId).resultList.singleOrNull() ?: return null

        val activity = entityManager.createQuery(
            """
            select min(publicReview.createdAt), count(publicReview.id)
            from Review publicReview
            where publicReview.author.id = :authorUserId
              and publicReview.visibilityStatus = :activeStatus
              and publicReview.currentSlot is not null
              and publicReview.deletedAt is null
            """.trimIndent(),
            Array<Any>::class.java,
        )
            .setParameter("authorUserId", review.author.id)
            .setParameter("activeStatus", ReviewVisibilityStatus.ACTIVE)
            .singleResult
        val firstPublicReviewAt = activity[0] as? java.time.Instant ?: review.author.createdAt
        val publicReviewCount = activity[1] as Long
        return StoredAdminReviewDetail(
            reviewId = review.id,
            authorUserId = review.author.id,
            authorStatus = review.author.status,
            firstPublicReviewAt = firstPublicReviewAt,
            publicReviewCount = publicReviewCount,
            restaurantId = review.restaurant.id,
            restaurantName = review.restaurant.brandName,
            restaurantStatus = review.restaurant.status,
            pickupLocationId = review.restaurant.pickupLocation.id,
            pickupAddress = review.restaurant.pickupLocation.standardAddress,
            visitMonth = review.visitMonth,
            ratings = review.ratings,
            comment = review.comment,
            commentStatus = review.commentModerationStatus,
            visibilityStatus = review.visibilityStatus,
            active = review.isActive,
            deletedAt = review.deletedAt,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
        )
    }

    override fun searchRestaurants(
        normalizedQuery: String,
        status: RestaurantStatus?,
        cursor: AdminRestaurantCursor?,
        limit: Int,
    ): List<StoredAdminRestaurantDetail> {
        val jpql = buildString {
            append(
                "select restaurant from Restaurant restaurant join fetch restaurant.pickupLocation pickupLocation " +
                    "left join fetch restaurant.canonicalRestaurant where " +
                    "(restaurant.normalizedName like :query or pickupLocation.normalizedAddress like :query)",
            )
            if (status != null) append(" and restaurant.status = :status")
            if (cursor != null) append(
                " and (restaurant.createdAt < :cursorCreatedAt or " +
                    "(restaurant.createdAt = :cursorCreatedAt and restaurant.id < :cursorId))",
            )
            append(" order by restaurant.createdAt desc, restaurant.id desc")
        }
        val query = entityManager.createQuery(jpql, Restaurant::class.java)
            .setParameter("query", "%$normalizedQuery%")
            .setMaxResults(limit)
        if (status != null) query.setParameter("status", status)
        if (cursor != null) {
            query.setParameter("cursorCreatedAt", cursor.createdAt)
            query.setParameter("cursorId", cursor.restaurantId)
        }
        return query.resultList.map(::restaurantSnapshot)
    }

    override fun findRestaurant(restaurantId: Long): StoredAdminRestaurantDetail? {
        val restaurant = entityManager.createQuery(
            """
            select restaurant
            from Restaurant restaurant
            join fetch restaurant.pickupLocation
            left join fetch restaurant.canonicalRestaurant
            where restaurant.id = :restaurantId
            """.trimIndent(),
            Restaurant::class.java,
        ).setParameter("restaurantId", restaurantId).resultList.singleOrNull() ?: return null
        return restaurantSnapshot(restaurant)
    }

    override fun findAudits(
        targetType: ModerationTargetType?,
        targetId: Long?,
        actorUserId: Long?,
        action: ModerationAuditAction?,
        cursor: ModerationAuditCursor?,
        limit: Int,
    ): List<StoredModerationAudit> {
        val clauses = mutableListOf<String>()
        if (targetType != null) clauses += "audit.targetType = :targetType"
        if (targetId != null) clauses += "audit.targetId = :targetId"
        if (actorUserId != null) clauses += "audit.actor.id = :actorUserId"
        if (action != null) clauses += "audit.action = :action"
        if (cursor != null) clauses +=
            "(audit.createdAt < :cursorCreatedAt or (audit.createdAt = :cursorCreatedAt and audit.id < :cursorId))"
        val jpql = buildString {
            append("select audit from ModerationAudit audit join fetch audit.actor")
            if (clauses.isNotEmpty()) append(" where ").append(clauses.joinToString(" and "))
            append(" order by audit.createdAt desc, audit.id desc")
        }
        val query = entityManager.createQuery(jpql, ModerationAudit::class.java).setMaxResults(limit)
        if (targetType != null) query.setParameter("targetType", targetType)
        if (targetId != null) query.setParameter("targetId", targetId)
        if (actorUserId != null) query.setParameter("actorUserId", actorUserId)
        if (action != null) query.setParameter("action", action)
        if (cursor != null) {
            query.setParameter("cursorCreatedAt", cursor.createdAt)
            query.setParameter("cursorId", cursor.auditId)
        }
        return query.resultList.map { audit ->
            StoredModerationAudit(
                audit.id,
                audit.actor.id,
                audit.action,
                audit.targetType,
                audit.targetId,
                audit.reason,
                audit.beforeState,
                audit.afterState,
                audit.occurredAt,
                audit.createdAt,
            )
        }
    }

    private fun restaurantSnapshot(restaurant: Restaurant): StoredAdminRestaurantDetail {
        val references = entityManager.createQuery(
            "select reference from RestaurantExternalReference reference where reference.restaurant.id = :restaurantId",
            RestaurantExternalReference::class.java,
        ).setParameter("restaurantId", restaurant.id).resultList.map {
            StoredAdminExternalReference(it.provider, it.externalPlaceId)
        }
        val platforms = entityManager.createQuery(
            "select platform from RestaurantPlatform platform where platform.restaurant.id = :restaurantId",
            RestaurantPlatform::class.java,
        ).setParameter("restaurantId", restaurant.id).resultList.mapTo(linkedSetOf()) { it.platform }
        val pendingReportCount = entityManager.createQuery(
            "select count(report.id) from RestaurantInfoReport report " +
                "where report.restaurant.id = :restaurantId and report.status = :status",
            java.lang.Long::class.java,
        )
            .setParameter("restaurantId", restaurant.id)
            .setParameter("status", ReportStatus.PENDING)
            .singleResult.toLong()
        return StoredAdminRestaurantDetail(
            restaurantId = restaurant.id,
            name = restaurant.brandName,
            normalizedName = restaurant.normalizedName,
            status = restaurant.status,
            canonicalRestaurantId = restaurant.canonicalRestaurant?.id,
            pickupLocationId = restaurant.pickupLocation.id,
            standardAddress = restaurant.pickupLocation.standardAddress,
            detailAddress = restaurant.pickupLocation.detailAddress,
            latitude = restaurant.pickupLocation.latitude,
            longitude = restaurant.pickupLocation.longitude,
            externalReferences = references,
            platforms = platforms,
            pendingReportCount = pendingReportCount,
            createdAt = restaurant.createdAt,
            updatedAt = restaurant.updatedAt,
        )
    }
}
