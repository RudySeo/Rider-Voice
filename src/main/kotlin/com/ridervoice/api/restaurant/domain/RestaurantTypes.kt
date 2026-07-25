package com.ridervoice.api.restaurant.domain

enum class PickupLocationSource {
    KAKAO,
    MANUAL_ADDRESS,
}

enum class RestaurantStatus {
    ACTIVE,
    MERGED,
}

enum class RestaurantExternalProvider {
    KAKAO,
}

enum class DeliveryPlatform {
    BAEMIN,
    COUPANG_EATS,
    YOGIYO,
    OTHER,
}
