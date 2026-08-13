package com.ridervoice.api.moderation.application.port.`in`

import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.model.RestaurantRenameResult
import com.ridervoice.api.moderation.application.model.RestaurantStatusChangeResult
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import java.math.BigDecimal

fun interface RelinkRestaurantPickupLocationUseCase {
    fun relinkPickupLocation(
        command: RelinkRestaurantPickupLocationCommand,
    ): RestaurantPickupRelinkResult
}

data class RelinkRestaurantPickupLocationCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val pickupLocationId: Long,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
    }
}

fun interface RenameRestaurantUseCase {
    fun rename(command: RenameRestaurantCommand): RestaurantRenameResult
}

data class RenameRestaurantCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val name: String,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
        require(RestaurantNormalization.displayText(name).isNotEmpty()) { "Restaurant name must not be blank" }
    }
}

enum class RestaurantStatusAction {
    CLOSE,
    REOPEN,
}

fun interface ChangeRestaurantStatusUseCase {
    fun changeStatus(command: ChangeRestaurantStatusCommand): RestaurantStatusChangeResult
}

data class ChangeRestaurantStatusCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val action: RestaurantStatusAction,
    val reason: String?,
) {
    init {
        require(adminUserId > 0) { "Administrator user ID must be positive" }
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }

    companion object {
        fun close(adminUserId: Long, restaurantId: Long, reason: String?) =
            ChangeRestaurantStatusCommand(adminUserId, restaurantId, RestaurantStatusAction.CLOSE, reason)

        fun reopen(adminUserId: Long, restaurantId: Long, reason: String?) =
            ChangeRestaurantStatusCommand(adminUserId, restaurantId, RestaurantStatusAction.REOPEN, reason)
    }
}

fun interface RelinkValidatedRestaurantPickupLocationUseCase {
    fun relinkValidatedPickupLocation(
        command: RelinkValidatedRestaurantPickupLocationCommand,
    ): RestaurantPickupRelinkResult
}

data class RelinkValidatedRestaurantPickupLocationCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val reason: String?,
)

fun interface RelinkRestaurantVerifiedAddressUseCase {
    fun relinkVerifiedAddress(command: RelinkRestaurantVerifiedAddressCommand): RestaurantPickupRelinkResult
}

data class RelinkRestaurantVerifiedAddressCommand(
    val adminUserId: Long,
    val restaurantId: Long,
    val addressQuery: String,
    val selectedStandardAddress: String,
    val detailAddress: String?,
    val reason: String?,
)
