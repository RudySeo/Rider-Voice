package com.ridervoice.api.restaurant.application.port.out

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult

fun interface KakaoKeywordSearchPort {
    fun search(query: String, limit: Int): ProviderSearchResult<ExternalRestaurantCandidate>
}

/** Public-search-only port. Registration revalidation must keep using [KakaoKeywordSearchPort]. */
fun interface PublicKakaoKeywordSearchPort {
    fun search(query: String, limit: Int): ProviderSearchResult<ExternalRestaurantCandidate>
}

fun interface KakaoAddressSearchPort {
    fun search(query: String, limit: Int): ProviderSearchResult<ExternalAddressCandidate>
}
