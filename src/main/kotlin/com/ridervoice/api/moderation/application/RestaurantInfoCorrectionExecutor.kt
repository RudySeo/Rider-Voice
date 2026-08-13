package com.ridervoice.api.moderation.application

import com.ridervoice.api.moderation.application.port.`in`.*
import com.ridervoice.api.moderation.application.model.RestaurantInfoReportResult
import com.ridervoice.api.restaurant.application.port.`in`.ValidateAddressSelectionCommand
import com.ridervoice.api.restaurant.application.port.`in`.ValidateAddressSelectionUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ValidatedAddressSelection
import org.springframework.stereotype.Component

internal interface RestaurantInfoCorrectionExecutor {
    fun prepare(correction: RestaurantInfoCorrectionCommand): PreparedRestaurantCorrection
    fun execute(
        adminUserId: Long,
        restaurantId: Long,
        correction: PreparedRestaurantCorrection,
        reason: String?,
    )
}

internal fun interface PreparedRestaurantInfoReportDecisionCoordinator {
    fun decidePrepared(
        command: DecideRestaurantInfoReportCommand,
        correction: PreparedRestaurantCorrection?,
    ): RestaurantInfoReportResult
}

@Component
internal class RestaurantInfoReportDecisionService(
    private val corrections: RestaurantInfoCorrectionExecutor,
    private val coordinator: PreparedRestaurantInfoReportDecisionCoordinator,
) : DecideRestaurantInfoReportUseCase {
    override fun decideRestaurantInfoReport(command: DecideRestaurantInfoReportCommand): RestaurantInfoReportResult {
        val prepared = command.correction?.let(corrections::prepare)
        return coordinator.decidePrepared(command, prepared)
    }
}

internal sealed interface PreparedRestaurantCorrection {
    data class Rename(val name: String) : PreparedRestaurantCorrection
    data class ExistingPickup(val pickupLocationId: Long) : PreparedRestaurantCorrection
    data class VerifiedAddress(val selection: ValidatedAddressSelection) : PreparedRestaurantCorrection
    data object Close : PreparedRestaurantCorrection
}

@Component
internal class DefaultRestaurantInfoCorrectionExecutor(
    private val addresses: ValidateAddressSelectionUseCase,
    private val rename: RenameRestaurantUseCase,
    private val relink: RelinkRestaurantPickupLocationUseCase,
    private val relinkValidated: RelinkValidatedRestaurantPickupLocationUseCase,
    private val status: ChangeRestaurantStatusUseCase,
) : RestaurantInfoCorrectionExecutor {
    override fun prepare(correction: RestaurantInfoCorrectionCommand): PreparedRestaurantCorrection = when (correction) {
        is RenameRestaurantCorrection -> PreparedRestaurantCorrection.Rename(correction.name)
        is RelinkExistingPickupCorrection -> PreparedRestaurantCorrection.ExistingPickup(correction.pickupLocationId)
        is RelinkVerifiedAddressCorrection -> PreparedRestaurantCorrection.VerifiedAddress(
            addresses.validate(
                ValidateAddressSelectionCommand(
                    correction.addressQuery,
                    correction.selectedStandardAddress,
                    correction.detailAddress,
                ),
            ),
        )
        CloseRestaurantCorrection -> PreparedRestaurantCorrection.Close
    }

    override fun execute(
        adminUserId: Long,
        restaurantId: Long,
        correction: PreparedRestaurantCorrection,
        reason: String?,
    ) {
        when (correction) {
            is PreparedRestaurantCorrection.Rename ->
                rename.rename(RenameRestaurantCommand(adminUserId, restaurantId, correction.name, reason))
            is PreparedRestaurantCorrection.ExistingPickup -> relink.relinkPickupLocation(
                RelinkRestaurantPickupLocationCommand(adminUserId, restaurantId, correction.pickupLocationId, reason),
            )
            is PreparedRestaurantCorrection.VerifiedAddress -> relinkValidated.relinkValidatedPickupLocation(
                correction.selection.let {
                    RelinkValidatedRestaurantPickupLocationCommand(
                        adminUserId,
                        restaurantId,
                        it.candidate.standardAddress,
                        it.detailAddress,
                        it.candidate.latitude,
                        it.candidate.longitude,
                        reason,
                    )
                },
            )
            PreparedRestaurantCorrection.Close ->
                status.changeStatus(ChangeRestaurantStatusCommand.close(adminUserId, restaurantId, reason))
        }
    }
}
