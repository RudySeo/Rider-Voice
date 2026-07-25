package com.ridervoice.api.restaurant.application.port.`in`

import com.ridervoice.api.restaurant.application.model.AddressSearchResult

fun interface SearchAddressesUseCase {
    fun search(command: SearchAddressesCommand): AddressSearchResult
}

data class SearchAddressesCommand(
    val userId: Long,
    val query: String,
) {
    init {
        require(userId > 0) { "User ID must be positive" }
        require(query.isNotBlank()) { "Address query must not be blank" }
    }
}
