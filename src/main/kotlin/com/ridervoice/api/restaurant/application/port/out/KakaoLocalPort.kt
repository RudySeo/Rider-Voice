package com.ridervoice.api.restaurant.application.port.out

import com.ridervoice.api.restaurant.application.model.PlaceCandidate

interface KakaoLocalPort {
    fun searchByKeyword(query: String): List<PlaceCandidate>
}
