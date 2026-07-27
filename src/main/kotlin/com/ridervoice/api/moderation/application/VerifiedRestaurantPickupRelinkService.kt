package com.ridervoice.api.moderation.application

import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantVerifiedAddressUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkValidatedRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkValidatedRestaurantPickupLocationUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ValidateAddressSelectionCommand
import com.ridervoice.api.restaurant.application.port.`in`.ValidateAddressSelectionUseCase
import org.springframework.stereotype.Service

@Service
internal class VerifiedRestaurantPickupRelinkService(
    private val addresses: ValidateAddressSelectionUseCase,
    private val relink: RelinkValidatedRestaurantPickupLocationUseCase,
) : RelinkRestaurantVerifiedAddressUseCase {
    override fun relinkVerifiedAddress(command: RelinkRestaurantVerifiedAddressCommand) =
        addresses.validate(
            ValidateAddressSelectionCommand(
                command.addressQuery,
                command.selectedStandardAddress,
                command.detailAddress,
            ),
        ).let { validated ->
            relink.relinkValidatedPickupLocation(
                RelinkValidatedRestaurantPickupLocationCommand(
                    command.adminUserId,
                    command.restaurantId,
                    validated.candidate.standardAddress,
                    validated.detailAddress,
                    validated.candidate.latitude,
                    validated.candidate.longitude,
                    command.reason,
                ),
            )
        }
}
