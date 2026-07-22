package com.ridervoice.api.common.persistence

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Clock
import java.time.Instant
import java.util.Optional

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
class JpaAuditingConfiguration {

    @Bean
    fun utcClock(): Clock = Clock.systemUTC()

    @Bean
    fun utcDateTimeProvider(utcClock: Clock): DateTimeProvider =
        DateTimeProvider { Optional.of(Instant.now(utcClock)) }
}
