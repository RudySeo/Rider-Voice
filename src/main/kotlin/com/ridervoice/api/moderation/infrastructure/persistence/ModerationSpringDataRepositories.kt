package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.ReviewReport
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

internal interface SpringDataReviewReportRepository : JpaRepository<ReviewReport, Long> {
    fun existsByReporterIdAndReviewId(reporterUserId: Long, reviewId: Long): Boolean

    fun countByReporterIdAndCreatedAtGreaterThanEqual(reporterUserId: Long, since: Instant): Long

    @Query(
        """
        select report
        from ReviewReport report
        where report.status = :status
        order by report.createdAt desc, report.id desc
        """,
    )
    fun findAllPending(
        @Param("status") status: ReportStatus,
        pageable: Pageable,
    ): List<ReviewReport>

    @Query(
        """
        select report
        from ReviewReport report
        where report.status = :status
          and (
              report.createdAt < :cursorCreatedAt
              or (report.createdAt = :cursorCreatedAt and report.id < :cursorId)
          )
        order by report.createdAt desc, report.id desc
        """,
    )
    fun findAllPendingBeforeCursor(
        @Param("status") status: ReportStatus,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<ReviewReport>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select report
        from ReviewReport report
        where report.id = :reportId
          and report.status = :status
        """,
    )
    fun findPendingForUpdate(
        @Param("reportId") reportId: Long,
        @Param("status") status: ReportStatus,
    ): Optional<ReviewReport>
}

internal interface SpringDataRestaurantInfoReportRepository : JpaRepository<RestaurantInfoReport, Long> {
    fun existsByReporterIdAndRestaurantId(reporterUserId: Long, restaurantId: Long): Boolean

    fun countByReporterIdAndCreatedAtGreaterThanEqual(reporterUserId: Long, since: Instant): Long

    @Query(
        """
        select report
        from RestaurantInfoReport report
        where report.status = :status
        order by report.createdAt desc, report.id desc
        """,
    )
    fun findAllPending(
        @Param("status") status: ReportStatus,
        pageable: Pageable,
    ): List<RestaurantInfoReport>

    @Query(
        """
        select report
        from RestaurantInfoReport report
        where report.status = :status
          and (
              report.createdAt < :cursorCreatedAt
              or (report.createdAt = :cursorCreatedAt and report.id < :cursorId)
          )
        order by report.createdAt desc, report.id desc
        """,
    )
    fun findAllPendingBeforeCursor(
        @Param("status") status: ReportStatus,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<RestaurantInfoReport>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select report
        from RestaurantInfoReport report
        where report.id = :reportId
          and report.status = :status
        """,
    )
    fun findPendingForUpdate(
        @Param("reportId") reportId: Long,
        @Param("status") status: ReportStatus,
    ): Optional<RestaurantInfoReport>
}

internal interface SpringDataModerationAuditRepository : JpaRepository<ModerationAudit, Long>
