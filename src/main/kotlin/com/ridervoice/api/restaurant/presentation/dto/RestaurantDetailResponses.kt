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
    @field:Schema(allowableValues = ["UNVERIFIED"])
    val verificationStatus: String,
    @field:Schema(example = "라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.")
    val verificationNotice: String,
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
)
