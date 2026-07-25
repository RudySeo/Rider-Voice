package com.ridervoice.api.restaurant.presentation

import com.ridervoice.api.restaurant.application.model.PublicRestaurantDetailResult
import com.ridervoice.api.restaurant.application.model.RestaurantAggregateMetricResult
import com.ridervoice.api.restaurant.presentation.dto.RestaurantAggregateMetricResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantBrandReportMetricsResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantBrandReportResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantDetailResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantPickupLocationReportMetricsResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantPickupLocationReportResponse
import com.ridervoice.api.restaurant.presentation.dto.RestaurantPickupLocationResponse
import org.springframework.stereotype.Component

@Component
class RestaurantDetailHttpMapper {
    fun toResponse(result: PublicRestaurantDetailResult) = RestaurantDetailResponse(
        restaurantId = result.restaurantId,
        name = result.name,
        pickupLocation = RestaurantPickupLocationResponse(
            pickupLocationId = result.pickupLocation.pickupLocationId,
            standardAddress = result.pickupLocation.standardAddress,
            detailAddress = result.pickupLocation.detailAddress,
            latitude = result.pickupLocation.latitude,
            longitude = result.pickupLocation.longitude,
        ),
        brandReport = RestaurantBrandReportResponse(
            status = result.brandReport.status,
            contributorCount = result.brandReport.contributorCount,
            metrics = result.brandReport.metrics?.let { metrics ->
                RestaurantBrandReportMetricsResponse(
                    metrics.packagingStability.toResponse(),
                    metrics.orderReadiness.toResponse(),
                    metrics.handoffAccuracy.toResponse(),
                )
            },
        ),
        pickupLocationReport = RestaurantPickupLocationReportResponse(
            status = result.pickupLocationReport.status,
            contributorCount = result.pickupLocationReport.contributorCount,
            metrics = result.pickupLocationReport.metrics?.let { metrics ->
                RestaurantPickupLocationReportMetricsResponse(
                    metrics.pickupSpaceCleanliness.toResponse(),
                    metrics.staffInteraction.toResponse(),
                    metrics.riderRespect.toResponse(),
                )
            },
        ),
        verificationStatus = result.verificationStatus,
        verificationNotice = result.verificationNotice,
    )

    private fun RestaurantAggregateMetricResult.toResponse() = RestaurantAggregateMetricResponse(
        observedCount = observedCount,
        notObservedCount = notObservedCount,
        distribution = distribution,
    )
}
