package com.ridervoice.api.restaurant.application

import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.restaurant.application.model.AggregationStatus
import com.ridervoice.api.restaurant.application.model.RestaurantAggregateMetricResult
import com.ridervoice.api.restaurant.application.model.RestaurantBrandReportResult
import com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportResult
import com.ridervoice.api.restaurant.application.model.StoredRestaurantDetail
import com.ridervoice.api.restaurant.application.port.out.RestaurantDetailQuery
import com.ridervoice.api.restaurant.application.port.out.RestaurantReportProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PublicRestaurantDetailServiceTest {

    @Test
    fun `merged restaurant ID returns canonical restaurant and reports without sibling brands`() {
        val canonical = detail(restaurantId = 20L)
        val detailQuery = RecordingDetailQuery(requestedId = 10L, result = canonical)
        val reports = RecordingReportProvider()
        val service = PublicRestaurantDetailService(detailQuery, reports)

        val result = service.get(10L)

        assertThat(detailQuery.receivedIds).containsExactly(10L)
        assertThat(reports.brandIds).containsExactly(20L)
        assertThat(reports.locationIds).containsExactly(30L)
        assertThat(result.restaurantId).isEqualTo(20L)
        assertThat(result.name).isEqualTo("대표 브랜드")
        assertThat(result.pickupLocation.pickupLocationId).isEqualTo(30L)
        assertThat(result.brandReport.status).isEqualTo(AggregationStatus.COLLECTING)
        assertThat(result.pickupLocationReport.status).isEqualTo(AggregationStatus.PUBLISHED)
        assertThat(result.verificationStatus).isEqualTo("UNVERIFIED")
        assertThat(result.verificationNotice).isEqualTo(PublicRestaurantDetailService.VERIFICATION_NOTICE)
        assertThat(result::class.java.declaredFields.map { it.name })
            .doesNotContain("restaurants", "brands", "siblingBrands")
    }

    @Test
    fun `unknown restaurant is not found and does not query reports`() {
        val reports = RecordingReportProvider()
        val service = PublicRestaurantDetailService(
            RecordingDetailQuery(requestedId = 404L, result = null),
            reports,
        )

        assertThatThrownBy { service.get(404L) }
            .isInstanceOf(ResourceNotFoundException::class.java)
        assertThat(reports.brandIds).isEmpty()
        assertThat(reports.locationIds).isEmpty()
    }

    private fun detail(restaurantId: Long) = StoredRestaurantDetail(
        restaurantId = restaurantId,
        name = "대표 브랜드",
        pickupLocationId = 30L,
        standardAddress = "서울 강남구 테헤란로 1",
        detailAddress = "지하 1층 픽업대",
        latitude = BigDecimal("37.50000000"),
        longitude = BigDecimal("127.00000000"),
    )

    private class RecordingDetailQuery(
        private val requestedId: Long,
        private val result: StoredRestaurantDetail?,
    ) : RestaurantDetailQuery {
        val receivedIds = mutableListOf<Long>()

        override fun findCanonicalDetail(restaurantId: Long): StoredRestaurantDetail? {
            receivedIds += restaurantId
            return result.takeIf { restaurantId == requestedId }
        }
    }

    private class RecordingReportProvider : RestaurantReportProvider {
        val brandIds = mutableListOf<Long>()
        val locationIds = mutableListOf<Long>()

        override fun getBrandReport(restaurantId: Long): RestaurantBrandReportResult {
            brandIds += restaurantId
            return RestaurantBrandReportResult(AggregationStatus.COLLECTING, 4, null)
        }

        override fun getPickupLocationReport(pickupLocationId: Long): RestaurantPickupLocationReportResult {
            locationIds += pickupLocationId
            val metric = RestaurantAggregateMetricResult(
                observedCount = 4,
                notObservedCount = 1,
                distribution = linkedMapOf(
                    "VERY_GOOD" to BigDecimal("50.0"),
                    "GOOD" to BigDecimal("25.0"),
                    "NEEDS_IMPROVEMENT" to BigDecimal("25.0"),
                    "MAJOR_IMPROVEMENT" to BigDecimal("0.0"),
                ),
            )
            return RestaurantPickupLocationReportResult(
                AggregationStatus.PUBLISHED,
                5,
                com.ridervoice.api.restaurant.application.model.RestaurantPickupLocationReportMetrics(
                    pickupSpaceCleanliness = metric,
                    staffInteraction = metric,
                    riderRespect = metric,
                ),
            )
        }
    }
}
