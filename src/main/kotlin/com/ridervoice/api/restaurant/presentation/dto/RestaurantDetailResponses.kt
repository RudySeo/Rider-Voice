package com.ridervoice.api.restaurant.presentation.dto

import com.ridervoice.api.restaurant.application.model.AggregationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import com.ridervoice.api.restaurant.domain.RestaurantStatus

data class RestaurantDetailResponse(
    @field:Schema(format = "int64")
    val restaurantId: Long,
    val name: String,
    val status: RestaurantStatus,
    val pickupLocation: RestaurantPickupLocationResponse,
    val brandReport: RestaurantBrandReportResponse,
    val pickupLocationReport: RestaurantPickupLocationReportResponse,
)

data class RestaurantPickupLocationResponse(
    @field:Schema(format = "int64")
    val pickupLocationId: Long,
    val standardAddress: String,
    @field:Schema(nullable = true)
    val detailAddress: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class RestaurantBrandReportResponse(
    val status: AggregationStatus,
    val contributorCount: Int,
    @field:Schema(nullable = true)
    val metrics: RestaurantBrandReportMetricsResponse?,
)

data class RestaurantPickupLocationReportResponse(
    val status: AggregationStatus,
    val contributorCount: Int,
    @field:Schema(nullable = true)
    val metrics: RestaurantPickupLocationReportMetricsResponse?,
)

data class RestaurantBrandReportMetricsResponse(
    val packagingStability: RestaurantAggregateMetricResponse,
    val orderReadiness: RestaurantAggregateMetricResponse,
    val handoffAccuracy: RestaurantAggregateMetricResponse,
)

data class RestaurantPickupLocationReportMetricsResponse(
    val pickupSpaceCleanliness: RestaurantAggregateMetricResponse,
    val staffInteraction: RestaurantAggregateMetricResponse,
    val riderRespect: RestaurantAggregateMetricResponse,
)

data class RestaurantAggregateMetricResponse(
    val observedCount: Int,
    val notObservedCount: Int,
    val distribution: Map<String, BigDecimal>,
    @field:Schema(nullable = true, description = "항목별 응답의 1.0~5.0 환산 점수. 종합 별점이 아닙니다.")
    val score: BigDecimal?,
)
