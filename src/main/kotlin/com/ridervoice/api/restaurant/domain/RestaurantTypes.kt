package com.ridervoice.api.restaurant.domain

enum class PickupLocationSource {
    KAKAO,
    MANUAL_ADDRESS,
    ADMIN_CORRECTION,
}

enum class RestaurantStatus {
    ACTIVE,
    CLOSED,
}

enum class DeliveryPlatform {
    BAEMIN,
    COUPANG_EATS,
    YOGIYO,
    OTHER,
}
