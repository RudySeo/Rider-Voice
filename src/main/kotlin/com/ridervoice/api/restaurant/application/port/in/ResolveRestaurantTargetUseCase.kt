package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.domain.DeliveryPlatform

fun interface ResolveRestaurantTargetUseCase {
    fun resolve(command: RestaurantTargetCommand): ResolvedRestaurantTargetResult
}

fun interface ValidateRestaurantTargetUseCase {
    fun validate(command: RestaurantTargetCommand): ValidatedRestaurantTarget
}

fun interface ResolveValidatedRestaurantTargetUseCase {
    fun resolve(target: ValidatedRestaurantTarget): ResolvedRestaurantTargetResult
}

sealed interface RestaurantTargetCommand {
    val type: RestaurantTargetType
}

enum class RestaurantTargetType {
    EXISTING,
    KAKAO,
    MANUAL_EXISTING_LOCATION,
    MANUAL_ADDRESS,
}

data class ExistingRestaurantTargetCommand(
    val restaurantId: Long,
) : RestaurantTargetCommand {
    override val type: RestaurantTargetType = RestaurantTargetType.EXISTING

    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}

data class KakaoRestaurantTargetCommand(
    val query: String,
    val kakaoPlaceId: String,
) : RestaurantTargetCommand {
    override val type: RestaurantTargetType = RestaurantTargetType.KAKAO

    init {
        require(query.isNotBlank()) { "Kakao search query must not be blank" }
        require(kakaoPlaceId.isNotBlank()) { "Kakao place ID must not be blank" }
    }
}

data class ManualExistingLocationRestaurantTargetCommand(
    val pickupLocationId: Long,
    val name: String,
    val platforms: Set<DeliveryPlatform>,
) : RestaurantTargetCommand {
    override val type: RestaurantTargetType = RestaurantTargetType.MANUAL_EXISTING_LOCATION

    init {
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
    }
}

data class ManualAddressRestaurantTargetCommand(
    val addressQuery: String,
    val selectedStandardAddress: String,
    val detailAddress: String?,
    val name: String,
    val platforms: Set<DeliveryPlatform>,
) : RestaurantTargetCommand {
    override val type: RestaurantTargetType = RestaurantTargetType.MANUAL_ADDRESS

    init {
        require(addressQuery.isNotBlank()) { "Address query must not be blank" }
        require(selectedStandardAddress.isNotBlank()) { "Selected standard address must not be blank" }
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
    }
}

sealed interface ValidatedRestaurantTarget

data class ValidatedExistingRestaurantTarget(
    val restaurantId: Long,
) : ValidatedRestaurantTarget {
    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}

data class ValidatedKakaoRestaurantTarget(
    val candidate: ExternalRestaurantCandidate,
) : ValidatedRestaurantTarget

data class ValidatedManualExistingLocationRestaurantTarget(
    val pickupLocationId: Long,
    val name: String,
    val platforms: Set<DeliveryPlatform>,
) : ValidatedRestaurantTarget {
    init {
        require(pickupLocationId > 0) { "Pickup location ID must be positive" }
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
    }
}

data class ValidatedManualAddressRestaurantTarget(
    val detailAddress: String?,
    val name: String,
    val platforms: Set<DeliveryPlatform>,
    val candidate: ExternalAddressCandidate,
) : ValidatedRestaurantTarget {
    init {
        require(name.isNotBlank()) { "Restaurant name must not be blank" }
    }
}

data class ResolvedRestaurantTargetResult(
    val restaurantId: Long,
) {
    init {
        require(restaurantId > 0) { "Restaurant ID must be positive" }
    }
}
