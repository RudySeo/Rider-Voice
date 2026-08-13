package com.ridervoice.api.moderation.presentation

import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.model.RestaurantRenameResult
import com.ridervoice.api.moderation.application.model.RestaurantStatusChangeResult
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusCommand
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.presentation.dto.RelinkRestaurantPickupLocationRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantPickupRelinkResponse
import com.ridervoice.api.moderation.presentation.dto.RenameRestaurantRequest
import com.ridervoice.api.moderation.presentation.dto.ChangeRestaurantStatusRequest
import com.ridervoice.api.moderation.presentation.dto.RestaurantRenameResponse
import com.ridervoice.api.moderation.presentation.dto.RestaurantStatusChangeResponse
import com.ridervoice.api.moderation.presentation.dto.RelinkRestaurantVerifiedAddressRequest
import org.springframework.stereotype.Component

@Component
class RestaurantAdministrationHttpMapper {
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

    fun toResponse(result: RestaurantPickupRelinkResult) = RestaurantPickupRelinkResponse(
        result.restaurantId,
        result.pickupLocationId,
        result.completedAt,
    )

    fun toRenameCommand(adminUserId: Long, restaurantId: Long, request: RenameRestaurantRequest) =
        RenameRestaurantCommand(adminUserId, restaurantId, request.name, request.reason)

    fun toStatusCommand(adminUserId: Long, restaurantId: Long, request: ChangeRestaurantStatusRequest) =
        ChangeRestaurantStatusCommand(adminUserId, restaurantId, requireNotNull(request.action), request.reason)

    fun toResponse(result: RestaurantRenameResult) =
        RestaurantRenameResponse(result.restaurantId, result.name, result.completedAt)

    fun toResponse(result: RestaurantStatusChangeResult) =
        RestaurantStatusChangeResponse(result.restaurantId, result.status, result.completedAt)

    fun toVerifiedRelinkCommand(
        adminUserId: Long,
        restaurantId: Long,
        request: RelinkRestaurantVerifiedAddressRequest,
    ) = RelinkRestaurantVerifiedAddressCommand(
        adminUserId,
        restaurantId,
        request.addressQuery,
        request.selectedStandardAddress,
        request.detailAddress,
        request.reason,
    )
}
