package com.ridervoice.api.review.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class VisitMonth(
    val value: YearMonth,
) {
    override fun toString(): String = value.toString()

    companion object {
        private val FORMAT = Regex("\\d{4}-\\d{2}")

        fun of(value: YearMonth): VisitMonth = VisitMonth(value)

        fun parse(value: String): VisitMonth {
            require(FORMAT.matches(value)) { "Visit month must use YYYY-MM format" }
            return try {
                VisitMonth(YearMonth.parse(value))
            } catch (exception: DateTimeParseException) {
                throw IllegalArgumentException("Visit month must be a valid calendar month", exception)
            }
        }
    }
}

@Converter(autoApply = false)
class VisitMonthAttributeConverter : AttributeConverter<VisitMonth, String> {
    override fun convertToDatabaseColumn(attribute: VisitMonth?): String? = attribute?.toString()

    override fun convertToEntityAttribute(dbData: String?): VisitMonth? = dbData?.let(VisitMonth::parse)
}

object VisitMonthPolicy {
    private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun isAllowed(visitMonth: VisitMonth, clock: Clock): Boolean {
        val currentMonth = YearMonth.from(clock.instant().atZone(SEOUL_ZONE))
        return visitMonth.value == currentMonth || visitMonth.value == currentMonth.minusMonths(1)
    }

    fun requireAllowed(visitMonth: VisitMonth, clock: Clock) {
        require(isAllowed(visitMonth, clock)) {
            "Visit month must be the current or previous month in Asia/Seoul"
        }
    }
}

object ReviewSubmissionPolicy {
    private val RESUBMISSION_INTERVAL: Duration = Duration.ofDays(90)

    fun canSubmit(
        activeReviewExists: Boolean,
        lastSubmittedAt: Instant?,
        submittedAt: Instant,
    ): Boolean = !activeReviewExists && (
        lastSubmittedAt == null || !submittedAt.isBefore(nextEligibleAt(lastSubmittedAt))
    )

    fun nextEligibleAt(lastSubmittedAt: Instant): Instant = lastSubmittedAt.plus(RESUBMISSION_INTERVAL)
}

internal object ReviewCommentPolicy {
    const val MAX_LENGTH: Int = 200

    fun normalize(comment: String?): String? = comment
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.also { require(it.length <= MAX_LENGTH) { "Review comment must not exceed 200 characters" } }
}
