package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult
import com.ridervoice.api.restaurant.application.model.PublicRestaurantPickupLocationResult
import com.ridervoice.api.restaurant.application.port.`in`.GetPublicRestaurantDetailUseCase
import com.ridervoice.api.restaurant.application.port.`in`.ResolveReadableRestaurantUseCase
import com.ridervoice.api.restaurant.application.port.out.RestaurantDetailQuery
import com.ridervoice.api.restaurant.application.port.out.RestaurantReportProvider
import org.springframework.stereotype.Service

@Service
class PublicRestaurantDetailService(
    private val restaurantDetails: RestaurantDetailQuery,
    private val reports: RestaurantReportProvider,
) : GetPublicRestaurantDetailUseCase, ResolveReadableRestaurantUseCase {

    override fun get(restaurantId: Long): PublicRestaurantDetailResult {
        val detail = restaurantDetails.findDetail(restaurantId)
            ?: throw ResourceNotFoundException("Restaurant not found")

        return PublicRestaurantDetailResult(
            restaurantId = detail.restaurantId,
            name = detail.name,
            status = detail.status,
            pickupLocation = PublicRestaurantPickupLocationResult(
                pickupLocationId = detail.pickupLocationId,
                standardAddress = detail.standardAddress,
                detailAddress = detail.detailAddress,
                latitude = detail.latitude,
                longitude = detail.longitude,
            ),
            brandReport = reports.getBrandReport(detail.restaurantId),
            pickupLocationReport = reports.getPickupLocationReport(detail.pickupLocationId),
        )
    }

    override fun resolve(restaurantId: Long): Long = restaurantDetails.findDetail(restaurantId)?.restaurantId
        ?: throw ResourceNotFoundException("Restaurant not found")

}
