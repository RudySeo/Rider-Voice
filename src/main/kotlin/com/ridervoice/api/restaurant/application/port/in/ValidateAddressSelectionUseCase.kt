package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate

fun interface ValidateAddressSelectionUseCase {
    fun validate(command: ValidateAddressSelectionCommand): ValidatedAddressSelection
}

data class ValidateAddressSelectionCommand(
    val addressQuery: String,
    val selectedStandardAddress: String,
    val detailAddress: String?,
)

data class ValidatedAddressSelection(
    val candidate: ExternalAddressCandidate,
    val detailAddress: String?,
)
