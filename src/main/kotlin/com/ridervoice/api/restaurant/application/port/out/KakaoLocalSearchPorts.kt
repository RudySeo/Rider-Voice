package com.ridervoice.api.restaurant.application.port.out

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult

fun interface KakaoKeywordSearchPort {
    fun search(query: String, limit: Int): ProviderSearchResult<ExternalRestaurantCandidate>
}

fun interface KakaoAddressSearchPort {
    fun search(query: String, limit: Int): ProviderSearchResult<ExternalAddressCandidate>
}
