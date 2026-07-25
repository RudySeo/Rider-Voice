package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.auth.domain.UserStatus
import com.ridervoice.api.moderation.domain.ModerationAudit
import com.ridervoice.api.moderation.domain.ReportStatus
import com.ridervoice.api.moderation.domain.RestaurantInfoReport
import com.ridervoice.api.moderation.domain.ReviewReport
import com.ridervoice.api.review.domain.Review
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.Repository
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

internal interface SpringDataModerationAdminRepository : Repository<User, Long> {
    fun existsByIdAndRoleAndStatus(userId: Long, role: UserRole, status: UserStatus): Boolean
}

internal interface SpringDataReviewCommentModerationRepository : JpaRepository<Review, Long> {
    @Query(
        """
        select review
        from Review review
        where review.commentModerationStatus = :commentStatus
          and review.visibilityStatus = :visibilityStatus
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllPendingComments(
        @Param("commentStatus") commentStatus: ReviewCommentStatus,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        pageable: Pageable,
    ): List<Review>

    @Query(
        """
        select review
        from Review review
        where review.commentModerationStatus = :commentStatus
          and review.visibilityStatus = :visibilityStatus
          and (
              review.createdAt < :cursorCreatedAt
              or (review.createdAt = :cursorCreatedAt and review.id < :cursorId)
          )
        order by review.createdAt desc, review.id desc
        """,
    )
    fun findAllPendingCommentsBeforeCursor(
        @Param("commentStatus") commentStatus: ReviewCommentStatus,
        @Param("visibilityStatus") visibilityStatus: ReviewVisibilityStatus,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<Review>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from Review review where review.id = :reviewId")
    fun findByIdForUpdate(@Param("reviewId") reviewId: Long): Optional<Review>
}
