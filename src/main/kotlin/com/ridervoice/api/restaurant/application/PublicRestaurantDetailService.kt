package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult
import com.ridervoice.api.restaurant.application.model.PublicRestaurantPickupLocationResult
import com.ridervoice.api.restaurant.application.port.`in`.GetPublicRestaurantDetailUseCase
import com.ridervoice.api.restaurant.application.port.out.RestaurantDetailQuery
import com.ridervoice.api.restaurant.application.port.out.RestaurantReportProvider
import org.springframework.stereotype.Service

@Service
class PublicRestaurantDetailService(
    private val restaurantDetails: RestaurantDetailQuery,
    private val reports: RestaurantReportProvider,
) : GetPublicRestaurantDetailUseCase {

    override fun get(restaurantId: Long): PublicRestaurantDetailResult {
        val detail = restaurantDetails.findCanonicalDetail(restaurantId)
            ?: throw ResourceNotFoundException("Restaurant not found")

        return PublicRestaurantDetailResult(
            restaurantId = detail.restaurantId,
            name = detail.name,
            pickupLocation = PublicRestaurantPickupLocationResult(
                pickupLocationId = detail.pickupLocationId,
                standardAddress = detail.standardAddress,
                detailAddress = detail.detailAddress,
                latitude = detail.latitude,
                longitude = detail.longitude,
            ),
            brandReport = reports.getBrandReport(detail.restaurantId),
            pickupLocationReport = reports.getPickupLocationReport(detail.pickupLocationId),
            verificationStatus = VERIFICATION_STATUS,
            verificationNotice = VERIFICATION_NOTICE,
        )
    }

    companion object {
        const val VERIFICATION_STATUS = "UNVERIFIED"
        const val VERIFICATION_NOTICE = "라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다."
    }
}
