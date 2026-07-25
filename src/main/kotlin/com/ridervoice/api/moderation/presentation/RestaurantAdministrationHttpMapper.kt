package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.moderation.application.model.RestaurantMergeResult
import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.presentation.dto.MergeRestaurantRequest
import com.ridervoice.api.moderation.presentation.dto.RelinkRestaurantPickupLocationRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantMergeResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantPickupRelinkResponse
import org.springframework.stereotype.Component

@Component
class RestaurantAdministrationHttpMapper {
    fun toMergeCommand(
        adminUserId: Long,
        duplicateRestaurantId: Long,
        request: MergeRestaurantRequest,
    ) = MergeRestaurantCommand(
        adminUserId = adminUserId,
        duplicateRestaurantId = duplicateRestaurantId,
        canonicalRestaurantId = requireNotNull(request.canonicalRestaurantId),
        reason = request.reason,
    )

    fun toRelinkCommand(
        adminUserId: Long,
        restaurantId: Long,
        request: RelinkRestaurantPickupLocationRequest,
    ) = RelinkRestaurantPickupLocationCommand(
        adminUserId = adminUserId,
        restaurantId = restaurantId,
        pickupLocationId = requireNotNull(request.pickupLocationId),
        reason = request.reason,
    )

    fun toResponse(result: RestaurantMergeResult) = RestaurantMergeResponse(
        result.restaurantId,
        result.status,
        result.canonicalRestaurantId,
        result.completedAt,
    )

    fun toResponse(result: RestaurantPickupRelinkResult) = RestaurantPickupRelinkResponse(
        result.restaurantId,
        result.pickupLocationId,
        result.completedAt,
    )
}
