package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.model.CommentModerationCursor
import com.ridervoice.api.moderation.application.port.`in`.DecideReviewCommentCommand
import com.ridervoice.api.moderation.application.port.`in`.ListPendingReviewCommentsQuery
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.ReviewCommentDecisionPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ReviewCommentModerationRepository
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.application.port.out.StoredReviewComment
import com.ridervoice.api.moderation.domain.CommentModerationDecision
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.review.domain.ReviewCommentStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class CommentModerationServiceTest {

    @Test
    fun `active admin can list only active pending comments with a stable cursor`() {
        val first = pendingComment(REVIEW_ID + 2, CREATED_AT.plusSeconds(2), "세 번째 의견")
        val second = pendingComment(REVIEW_ID + 1, CREATED_AT.plusSeconds(1), "두 번째 의견")
        val third = pendingComment(REVIEW_ID, CREATED_AT, "첫 번째 의견")
        val processed = third.copy(
            reviewId = REVIEW_ID - 1,
            commentModerationStatus = ReviewCommentStatus.PUBLISHED,
        )
        val excluded = third.copy(
            reviewId = REVIEW_ID - 2,
            visibilityStatus = ReviewVisibilityStatus.EXCLUDED,
        )
        val fixture = fixture(first, second, third, processed, excluded)

        val result = fixture.service.list(
            ListPendingReviewCommentsQuery(ADMIN_ID, cursor = null, size = 2),
        )

        assertThat(result.items.map { it.reviewId }).containsExactly(first.reviewId, second.reviewId)
        assertThat(result.items.map { it.comment }).containsExactly("세 번째 의견", "두 번째 의견")
        assertThat(result.nextCursor).isEqualTo(
            CommentModerationCursor(second.createdAt, second.reviewId),
        )
        assertThat(fixture.comments.requestedLimit).isEqualTo(3)
    }

    @Test
    fun `approve publishes the current pending comment and appends its audit in the transaction`() {
        val fixture = fixture(pendingComment())

        val result = fixture.service.decide(
            DecideReviewCommentCommand(ADMIN_ID, REVIEW_ID, CommentModerationDecision.APPROVE),
        )

        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
        assertThat(fixture.comments.current(REVIEW_ID)?.commentModerationStatus)
            .isEqualTo(ReviewCommentStatus.PUBLISHED)
        assertAudit(
            fixture.audits.commands.single(),
            ModerationAuditAction.COMMENT_APPROVED,
            ReviewCommentStatus.PUBLISHED,
        )
        assertDecisionMethodIsTransactional()
    }

    @Test
    fun `reject hides only the comment and keeps structured review visibility active`() {
        val fixture = fixture(pendingComment())

        val result = fixture.service.decide(
            DecideReviewCommentCommand(ADMIN_ID, REVIEW_ID, CommentModerationDecision.REJECT),
        )

        assertThat(result.commentModerationStatus).isEqualTo(ReviewCommentStatus.REJECTED)
        assertThat(fixture.comments.current(REVIEW_ID)?.visibilityStatus)
            .isEqualTo(ReviewVisibilityStatus.ACTIVE)
        assertAudit(
            fixture.audits.commands.single(),
            ModerationAuditAction.COMMENT_REJECTED,
            ReviewCommentStatus.REJECTED,
        )
    }

    @Test
    fun `an edited published comment is moderated only after it has returned to pending`() {
        val editedPending = pendingComment(comment = "수정 후 다시 검수할 의견").copy(
            updatedAt = CREATED_AT.plusSeconds(60),
        )
        val fixture = fixture(editedPending)

        val listed = fixture.service.list(ListPendingReviewCommentsQuery(ADMIN_ID, null, 20))
        val decided = fixture.service.decide(
            DecideReviewCommentCommand(ADMIN_ID, REVIEW_ID, CommentModerationDecision.APPROVE),
        )

        assertThat(listed.items.single().comment).isEqualTo("수정 후 다시 검수할 의견")
        assertThat(decided.commentModerationStatus).isEqualTo(ReviewCommentStatus.PUBLISHED)
    }

    @Test
    fun `processed target is a stable conflict and missing target is stable not found`() {
        val processed = pendingComment().copy(commentModerationStatus = ReviewCommentStatus.PUBLISHED)
        val fixture = fixture(processed)

        assertThatThrownBy {
            fixture.service.decide(
                DecideReviewCommentCommand(ADMIN_ID, REVIEW_ID, CommentModerationDecision.REJECT),
            )
        }.isInstanceOf(StateConflictException::class.java)

        assertThatThrownBy {
            fixture.service.decide(
                DecideReviewCommentCommand(ADMIN_ID, REVIEW_ID + 1, CommentModerationDecision.REJECT),
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)

        assertThat(fixture.audits.commands).isEmpty()
    }

    @Test
    fun `service rejects a non-admin actor before reading or changing moderation targets`() {
        val fixture = fixture(pendingComment(), activeAdmin = false)

        assertThatThrownBy {
            fixture.service.list(ListPendingReviewCommentsQuery(USER_ID, null, 20))
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.errorCode).isEqualTo(ApiErrorCode.ACCESS_DENIED)
        }
        assertThatThrownBy {
            fixture.service.decide(
                DecideReviewCommentCommand(USER_ID, REVIEW_ID, CommentModerationDecision.APPROVE),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.errorCode).isEqualTo(ApiErrorCode.ACCESS_DENIED)
        }

        assertThat(fixture.comments.readCount).isZero()
        assertThat(fixture.audits.commands).isEmpty()
    }

    @Test
    fun `concurrent duplicate decisions serialize into one success one conflict and one audit`() {
        val fixture = fixture(pendingComment())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val calls = listOf(
            Callable {
                ready.countDown()
                start.await(2, TimeUnit.SECONDS)
                runCatching {
                    fixture.service.decide(
                        DecideReviewCommentCommand(
                            ADMIN_ID,
                            REVIEW_ID,
                            CommentModerationDecision.APPROVE,
                        ),
                    )
                }
            },
            Callable {
                ready.countDown()
                start.await(2, TimeUnit.SECONDS)
                runCatching {
                    fixture.service.decide(
                        DecideReviewCommentCommand(
                            ADMIN_ID,
                            REVIEW_ID,
                            CommentModerationDecision.REJECT,
                        ),
                    )
                }
            },
        )

        try {
            val futures = calls.map(executor::submit)
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val results = futures.map { it.get(2, TimeUnit.SECONDS) }

            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.mapNotNull { it.exceptionOrNull() })
                .singleElement()
                .isInstanceOf(StateConflictException::class.java)
            assertThat(fixture.audits.commands).hasSize(1)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun fixture(
        vararg comments: StoredReviewComment,
        activeAdmin: Boolean = true,
    ): Fixture {
        val commentRepository = FakeReviewCommentModerationRepository(comments.toList())
        val auditRepository = FakeModerationAuditRepository()
        val service = CommentModerationService(
            admins = ModerationAdminRepository { activeAdmin },
            comments = commentRepository,
            audits = auditRepository,
            clock = Clock.fixed(DECIDED_AT, ZoneOffset.UTC),
        )
        return Fixture(service, commentRepository, auditRepository)
    }

    private fun pendingComment(
        reviewId: Long = REVIEW_ID,
        createdAt: Instant = CREATED_AT,
        comment: String = "검수할 의견",
    ) = StoredReviewComment(
        reviewId = reviewId,
        authorUserId = AUTHOR_ID,
        comment = comment,
        commentModerationStatus = ReviewCommentStatus.PENDING,
        visibilityStatus = ReviewVisibilityStatus.ACTIVE,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun assertAudit(
        command: ModerationAuditPersistenceCommand,
        action: ModerationAuditAction,
        afterStatus: ReviewCommentStatus,
    ) {
        assertThat(command.actorUserId).isEqualTo(ADMIN_ID)
        assertThat(command.action).isEqualTo(action)
        assertThat(command.targetType).isEqualTo(ModerationTargetType.REVIEW)
        assertThat(command.targetId).isEqualTo(REVIEW_ID)
        assertThat(command.beforeState).isEqualTo("{\"commentModerationStatus\":\"PENDING\"}")
        assertThat(command.afterState)
            .isEqualTo("{\"commentModerationStatus\":\"${afterStatus.name}\"}")
        assertThat(command.occurredAt).isEqualTo(DECIDED_AT)
    }

    private fun assertDecisionMethodIsTransactional() {
        val annotation = CommentModerationService::class.java
            .getMethod("decide", DecideReviewCommentCommand::class.java)
            .getAnnotation(Transactional::class.java)

        assertThat(annotation).isNotNull
        assertThat(annotation.readOnly).isFalse()
    }

    private data class Fixture(
        val service: CommentModerationService,
        val comments: FakeReviewCommentModerationRepository,
        val audits: FakeModerationAuditRepository,
    )

    private class FakeReviewCommentModerationRepository(
        comments: List<StoredReviewComment>,
    ) : ReviewCommentModerationRepository {
        private val rows = comments.associateByTo(mutableMapOf()) { it.reviewId }
        private val decisionLock = ReentrantLock()

        @Volatile
        var requestedLimit: Int? = null

        @Volatile
        var readCount: Int = 0

        override fun findPending(
            cursor: CommentModerationCursor?,
            limit: Int,
        ): List<StoredReviewComment> {
            readCount++
            requestedLimit = limit
            return synchronized(rows) {
                rows.values.asSequence()
                    .filter { it.commentModerationStatus == ReviewCommentStatus.PENDING }
                    .filter { it.visibilityStatus == ReviewVisibilityStatus.ACTIVE }
                    .filter {
                        cursor == null || it.createdAt < cursor.createdAt ||
                            (it.createdAt == cursor.createdAt && it.reviewId < cursor.reviewId)
                    }
                    .sortedWith(
                        compareByDescending<StoredReviewComment> { it.createdAt }
                            .thenByDescending { it.reviewId },
                    )
                    .take(limit)
                    .toList()
            }
        }

        override fun findForUpdate(reviewId: Long): StoredReviewComment? {
            readCount++
            decisionLock.lock()
            val row = synchronized(rows) { rows[reviewId] }
            if (row?.commentModerationStatus != ReviewCommentStatus.PENDING) {
                decisionLock.unlock()
            }
            return row
        }

        override fun saveDecision(
            command: ReviewCommentDecisionPersistenceCommand,
        ): StoredReviewComment = try {
            synchronized(rows) {
                val current = requireNotNull(rows[command.reviewId])
                check(current.commentModerationStatus == command.expectedStatus)
                current.copy(commentModerationStatus = command.nextStatus).also {
                    rows[command.reviewId] = it
                }
            }
        } finally {
            decisionLock.unlock()
        }

        fun current(reviewId: Long): StoredReviewComment? = synchronized(rows) { rows[reviewId] }
    }

    private class FakeModerationAuditRepository : ModerationAuditRepository {
        val commands: MutableList<ModerationAuditPersistenceCommand> =
            Collections.synchronizedList(mutableListOf())

        override fun append(command: ModerationAuditPersistenceCommand): StoredModerationAudit {
            commands += command
            return StoredModerationAudit(
                auditId = commands.size.toLong(),
                actorUserId = command.actorUserId,
                action = command.action,
                targetType = command.targetType,
                targetId = command.targetId,
                reason = command.reason,
                beforeState = command.beforeState,
                afterState = command.afterState,
                occurredAt = command.occurredAt,
                createdAt = command.occurredAt,
            )
        }
    }

    private companion object {
        const val ADMIN_ID = 8L
        const val USER_ID = 9L
        const val AUTHOR_ID = 7L
        const val REVIEW_ID = 40L
        val CREATED_AT: Instant = Instant.parse("2026-07-25T03:00:00Z")
        val DECIDED_AT: Instant = Instant.parse("2026-07-26T03:00:00Z")
    }
}
