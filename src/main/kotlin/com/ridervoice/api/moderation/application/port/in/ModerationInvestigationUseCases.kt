package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.AdminRestaurantCursor
import com.ridervoice.api.moderation.application.model.AdminRestaurantDetailResult
import com.ridervoice.api.moderation.application.model.AdminRestaurantSearchPageResult
import com.ridervoice.api.moderation.application.model.AdminReviewDetailResult
import com.ridervoice.api.moderation.application.model.ModerationAuditCursor
import com.ridervoice.api.moderation.application.model.ModerationAuditPageResult
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.RestaurantStatus

fun interface GetAdminReviewDetailUseCase {
    fun get(query: GetAdminReviewDetailQuery): AdminReviewDetailResult
}

data class GetAdminReviewDetailQuery(val adminUserId: Long, val reviewId: Long)

fun interface SearchAdminRestaurantsUseCase {
    fun search(query: SearchAdminRestaurantsQuery): AdminRestaurantSearchPageResult
}

data class SearchAdminRestaurantsQuery(
    val adminUserId: Long,
    val query: String,
    val status: RestaurantStatus?,
    val cursor: AdminRestaurantCursor?,
    val size: Int,
)

fun interface GetAdminRestaurantDetailUseCase {
    fun get(query: GetAdminRestaurantDetailQuery): AdminRestaurantDetailResult
}

data class GetAdminRestaurantDetailQuery(val adminUserId: Long, val restaurantId: Long)

fun interface ListModerationAuditsUseCase {
    fun list(query: ListModerationAuditsQuery): ModerationAuditPageResult
}

data class ListModerationAuditsQuery(
    val adminUserId: Long,
    val targetType: ModerationTargetType?,
    val targetId: Long?,
    val actorUserId: Long?,
    val action: ModerationAuditAction?,
    val cursor: ModerationAuditCursor?,
    val size: Int,
)
