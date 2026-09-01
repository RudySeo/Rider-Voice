package com.ridervoice.api.restaurant.infrastructure.report

import com.ridervoice.api.restaurant.application.model.RestaurantAggregateMetricResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportMetrics
import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandSummary
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportMetrics
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportResult
import com.ridervoice.api.restaurant.application.port.out.RestaurantReportProvider
import com.ridervoice.api.restaurant.application.port.out.RestaurantSearchReviewSummaryProvider
import com.ridervoice.api.review.application.model.AggregateMetricResult
import com.ridervoice.api.review.application.port.`in`.ReviewAggregateUseCase
import org.springframework.stereotype.Component

@Component
internal class ReviewAggregateRestaurantReportProvider(
    private val aggregates: ReviewAggregateUseCase,
) : RestaurantReportProvider, RestaurantSearchReviewSummaryProvider {

    override fun findByRestaurantIds(
        restaurantIds: Set<Long>,
    ): Map<Long, RestaurantBrandSummary> = aggregates.getBrandSummaries(restaurantIds)
        .mapValues { (_, summary) ->
            RestaurantBrandSummary(
                status = summary.status,
                contributorCount = summary.contributorCount,
            )
        }

    override fun getBrandReport(restaurantId: Long): RestaurantBrandReportResult {
        val report = aggregates.getBrandReport(restaurantId)
        return RestaurantBrandReportResult(
            status = report.status,
            contributorCount = report.contributorCount,
            metrics = report.metrics?.let { metrics ->
                RestaurantBrandReportMetrics(
                    packagingStability = metrics.packagingStability.toRestaurantMetric(),
                    orderReadiness = metrics.orderReadiness.toRestaurantMetric(),
                    handoffAccuracy = metrics.handoffAccuracy.toRestaurantMetric(),
                )
            },
        )
    }

    override fun getPickupLocationReport(pickupLocationId: Long): RestaurantPickupLocationReportResult {
        val report = aggregates.getPickupLocationReport(pickupLocationId)
        return RestaurantPickupLocationReportResult(
            status = report.status,
            contributorCount = report.contributorCount,
            metrics = report.metrics?.let { metrics ->
                RestaurantPickupLocationReportMetrics(
                    pickupSpaceCleanliness = metrics.pickupSpaceCleanliness.toRestaurantMetric(),
                    staffInteraction = metrics.staffInteraction.toRestaurantMetric(),
                    riderRespect = metrics.riderRespect.toRestaurantMetric(),
                )
            },
        )
    }

    private fun AggregateMetricResult.toRestaurantMetric() = RestaurantAggregateMetricResult(
        observedCount = observedCount,
        notObservedCount = notObservedCount,
        distribution = distribution.mapKeys { (rating, _) -> rating.name },
        score = score,
    )
}
