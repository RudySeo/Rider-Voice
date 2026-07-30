package com.ridervoice.api.review.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReviewPolicyTest {

    @Test
    fun `visit month accepts the current and previous month in Asia Seoul`() {
        val clock = Clock.fixed(Instant.parse("2026-07-31T15:00:00Z"), ZoneOffset.UTC)

        assertThat(VisitMonthPolicy.isAllowed(VisitMonth.parse("2026-08"), clock)).isTrue()
        assertThat(VisitMonthPolicy.isAllowed(VisitMonth.parse("2026-07"), clock)).isTrue()
        assertThat(VisitMonthPolicy.isAllowed(VisitMonth.parse("2026-06"), clock)).isFalse()
        assertThat(VisitMonthPolicy.isAllowed(VisitMonth.parse("2026-09"), clock)).isFalse()
    }

    @Test
    fun `visit month uses strict year month format`() {
        assertThat(VisitMonth.parse("2026-07").toString()).isEqualTo("2026-07")

        assertThatIllegalArgumentException().isThrownBy { VisitMonth.parse("2026-7") }
        assertThatIllegalArgumentException().isThrownBy { VisitMonth.parse("2026-13") }
    }

    @Test
    fun `active review blocks creation and inactive review permits creation at the ninety day boundary`() {
        val lastSubmittedAt = Instant.parse("2026-01-01T00:00:00Z")
        val boundary = Instant.parse("2026-04-01T00:00:00Z")

        assertThat(ReviewSubmissionPolicy.canSubmit(false, null, lastSubmittedAt)).isTrue()
        assertThat(ReviewSubmissionPolicy.canSubmit(true, lastSubmittedAt, boundary.plusSeconds(1))).isFalse()
        assertThat(ReviewSubmissionPolicy.canSubmit(false, lastSubmittedAt, boundary.minusNanos(1))).isFalse()
        assertThat(ReviewSubmissionPolicy.canSubmit(false, lastSubmittedAt, boundary)).isTrue()
        assertThat(ReviewSubmissionPolicy.canSubmit(false, lastSubmittedAt, boundary.plusSeconds(1))).isTrue()
        assertThat(ReviewSubmissionPolicy.nextEligibleAt(lastSubmittedAt)).isEqualTo(boundary)
    }
}
