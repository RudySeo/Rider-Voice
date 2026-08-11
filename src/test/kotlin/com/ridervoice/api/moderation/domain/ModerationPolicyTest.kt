package com.ridervoice.api.moderation.domain

import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class ModerationPolicyTest {

    @Test
    fun `report types expose provider independent status reasons and decisions`() {
        assertThat(ReportStatus.entries).containsExactly(
            ReportStatus.PENDING,
            ReportStatus.RESOLVED,
        )
        assertThat(ReviewReportReason.entries).containsExactly(
            ReviewReportReason.PERSONAL_INFORMATION,
            ReviewReportReason.ABUSIVE_CONTENT,
            ReviewReportReason.IRRELEVANT_CONTENT,
            ReviewReportReason.FALSE_INFORMATION,
            ReviewReportReason.SPAM,
            ReviewReportReason.OTHER,
        )
        assertThat(RestaurantInfoReportReason.entries).containsExactly(
            RestaurantInfoReportReason.INCORRECT_NAME,
            RestaurantInfoReportReason.INCORRECT_PICKUP_LOCATION,
            RestaurantInfoReportReason.DUPLICATE,
            RestaurantInfoReportReason.CLOSED,
            RestaurantInfoReportReason.OTHER,
        )
        assertThat(ReviewReportDecision.entries).containsExactly(
            ReviewReportDecision.DISMISS,
            ReviewReportDecision.HIDE_COMMENT,
            ReviewReportDecision.EXCLUDE_REVIEW,
        )
        assertThat(RestaurantInfoReportDecision.entries).containsExactly(
            RestaurantInfoReportDecision.DISMISS,
            RestaurantInfoReportDecision.RESOLVE,
        )
    }

    @Test
    fun `review report receipt hides only a published comment and never structured ratings`() {
        ReviewCommentStatus.entries.forEach { status ->
            val transition = ModerationTransitionPolicy.receiveReviewReport(
                reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                commentStatus = status,
            )

            assertThat(transition.reportStatus).isEqualTo(ReportStatus.PENDING)
            assertThat(transition.reviewVisibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
            assertThat(transition.commentStatus).isEqualTo(
                if (status == ReviewCommentStatus.PUBLISHED) {
                    ReviewCommentStatus.HIDDEN_REPORTED
                } else {
                    status
                },
            )
        }
    }

    @Test
    fun `excluded review cannot receive another report`() {
        assertThatIllegalStateException().isThrownBy {
            ModerationTransitionPolicy.receiveReviewReport(
                reviewVisibilityStatus = ReviewVisibilityStatus.EXCLUDED,
                commentStatus = ReviewCommentStatus.PUBLISHED,
            )
        }
    }

    @Test
    fun `dismiss resolves report and restores only a comment hidden by that report`() {
        val hidden = ModerationTransitionPolicy.decideReviewReport(
            reportStatus = ReportStatus.PENDING,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
            commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
            decision = ReviewReportDecision.DISMISS,
        )
        val pending = ModerationTransitionPolicy.decideReviewReport(
            reportStatus = ReportStatus.PENDING,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
            commentStatus = ReviewCommentStatus.PENDING,
            decision = ReviewReportDecision.DISMISS,
        )

        assertThat(hidden.commentStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
        assertThat(pending.commentStatus).isEqualTo(ReviewCommentStatus.PENDING)
        assertThat(hidden.reportStatus).isEqualTo(ReportStatus.RESOLVED)
        assertThat(hidden.reviewVisibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertThat(hidden.currentPointerAction).isEqualTo(CurrentReviewPointerAction.KEEP)
        assertThat(hidden.cooldownAction).isEqualTo(ReviewCooldownAction.RETAIN)
        assertThat(hidden.auditAction).isEqualTo(ModerationAuditAction.REVIEW_REPORT_DISMISSED)
    }

    @Test
    fun `dismiss accepts every active comment state and changes only report-hidden comment`() {
        ReviewCommentStatus.entries.forEach { status ->
            val transition = ModerationTransitionPolicy.decideReviewReport(
                reportStatus = ReportStatus.PENDING,
                reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                commentStatus = status,
                decision = ReviewReportDecision.DISMISS,
            )

            assertThat(transition.commentStatus).isEqualTo(
                if (status == ReviewCommentStatus.HIDDEN_REPORTED) {
                    ReviewCommentStatus.PUBLISHED
                } else {
                    status
                },
            )
        }
    }

    @Test
    fun `comment hide resolves report without hiding structured ratings or clearing current pointer`() {
        val transition = ModerationTransitionPolicy.decideReviewReport(
            reportStatus = ReportStatus.PENDING,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
            commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
            decision = ReviewReportDecision.HIDE_COMMENT,
        )

        assertThat(transition.reportStatus).isEqualTo(ReportStatus.RESOLVED)
        assertThat(transition.commentStatus).isEqualTo(ReviewCommentStatus.REJECTED)
        assertThat(transition.reviewVisibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertThat(transition.currentPointerAction).isEqualTo(CurrentReviewPointerAction.KEEP)
        assertThat(transition.cooldownAction).isEqualTo(ReviewCooldownAction.RETAIN)
        assertThat(transition.auditAction).isEqualTo(ModerationAuditAction.REVIEW_COMMENT_HIDDEN)
    }

    @Test
    fun `comment hide requires the report-hidden comment state`() {
        ReviewCommentStatus.entries
            .filterNot { it in setOf(ReviewCommentStatus.HIDDEN_REPORTED, ReviewCommentStatus.REJECTED) }
            .forEach { status ->
                assertThatIllegalStateException().isThrownBy {
                    ModerationTransitionPolicy.decideReviewReport(
                        reportStatus = ReportStatus.PENDING,
                        reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                        commentStatus = status,
                        decision = ReviewReportDecision.HIDE_COMMENT,
                    )
                }
            }
    }

    @Test
    fun `comment hide is idempotent after another report already rejected the comment`() {
        val transition = ModerationTransitionPolicy.decideReviewReport(
            reportStatus = ReportStatus.PENDING,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
            commentStatus = ReviewCommentStatus.REJECTED,
            decision = ReviewReportDecision.HIDE_COMMENT,
        )

        assertThat(transition.commentStatus).isEqualTo(ReviewCommentStatus.REJECTED)
        assertThat(transition.reviewVisibilityStatus).isEqualTo(ReviewVisibilityStatus.ACTIVE)
    }

    @Test
    fun `full exclusion hides review and requires target pointer clear while retaining cooldown`() {
        val transition = ModerationTransitionPolicy.decideReviewReport(
            reportStatus = ReportStatus.PENDING,
            reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
            commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
            decision = ReviewReportDecision.EXCLUDE_REVIEW,
        )

        assertThat(transition.reportStatus).isEqualTo(ReportStatus.RESOLVED)
        assertThat(transition.reviewVisibilityStatus).isEqualTo(ReviewVisibilityStatus.EXCLUDED)
        assertThat(transition.currentPointerAction).isEqualTo(CurrentReviewPointerAction.CLEAR_IF_TARGET)
        assertThat(transition.cooldownAction).isEqualTo(ReviewCooldownAction.RETAIN)
        assertThat(transition.auditAction).isEqualTo(ModerationAuditAction.REVIEW_EXCLUDED)
    }

    @Test
    fun `full exclusion accepts every comment state without changing the comment state`() {
        ReviewCommentStatus.entries.forEach { status ->
            val transition = ModerationTransitionPolicy.decideReviewReport(
                reportStatus = ReportStatus.PENDING,
                reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                commentStatus = status,
                decision = ReviewReportDecision.EXCLUDE_REVIEW,
            )

            assertThat(transition.commentStatus).isEqualTo(status)
            assertThat(transition.auditAction).isEqualTo(ModerationAuditAction.REVIEW_EXCLUDED)
        }
    }

    @Test
    fun `review report decision rejects resolved reports and excluded reviews`() {
        ReviewReportDecision.entries.forEach { decision ->
            assertThatIllegalStateException().isThrownBy {
                ModerationTransitionPolicy.decideReviewReport(
                    reportStatus = ReportStatus.RESOLVED,
                    reviewVisibilityStatus = ReviewVisibilityStatus.ACTIVE,
                    commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                    decision = decision,
                )
            }
            assertThatIllegalStateException().isThrownBy {
                ModerationTransitionPolicy.decideReviewReport(
                    reportStatus = ReportStatus.PENDING,
                    reviewVisibilityStatus = ReviewVisibilityStatus.EXCLUDED,
                    commentStatus = ReviewCommentStatus.HIDDEN_REPORTED,
                    decision = decision,
                )
            }
        }
    }

    @Test
    fun `restaurant info report decisions resolve once and require audit`() {
        val dismissed = ModerationTransitionPolicy.decideRestaurantInfoReport(
            ReportStatus.PENDING,
            RestaurantInfoReportDecision.DISMISS,
        )
        val resolved = ModerationTransitionPolicy.decideRestaurantInfoReport(
            ReportStatus.PENDING,
            RestaurantInfoReportDecision.RESOLVE,
        )

        assertThat(dismissed).isEqualTo(
            RestaurantInfoReportTransition(
                reportStatus = ReportStatus.RESOLVED,
                auditAction = ModerationAuditAction.RESTAURANT_REPORT_DISMISSED,
            ),
        )
        assertThat(resolved).isEqualTo(
            RestaurantInfoReportTransition(
                reportStatus = ReportStatus.RESOLVED,
                auditAction = ModerationAuditAction.RESTAURANT_INFO_CORRECTED,
            ),
        )
        RestaurantInfoReportDecision.entries.forEach { decision ->
            assertThatIllegalStateException().isThrownBy {
                ModerationTransitionPolicy.decideRestaurantInfoReport(ReportStatus.RESOLVED, decision)
            }
        }
    }

    @Test
    fun `restaurant merge and pickup relink have distinct required audit actions`() {
        assertThat(ModerationAuditPolicy.actionFor(RestaurantAdminAction.MERGE_DUPLICATE))
            .isEqualTo(ModerationAuditAction.DUPLICATE_RESTAURANT_MERGED)
        assertThat(ModerationAuditPolicy.actionFor(RestaurantAdminAction.RELINK_PICKUP_LOCATION))
            .isEqualTo(ModerationAuditAction.RESTAURANT_PICKUP_RELINKED)
    }
}
