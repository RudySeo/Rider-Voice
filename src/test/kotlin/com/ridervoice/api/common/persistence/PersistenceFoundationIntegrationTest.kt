package com.ridervoice.api.common.persistence

import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class PersistenceFoundationIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway migration succeeds and JPA context boots on MySQL with UTC session`() {
        val currentMigration = requireNotNull(flyway.info().current())

        assertThat(currentMigration.version.version).isEqualTo("3")
        assertThat(currentMigration.state).isEqualTo(MigrationState.SUCCESS)
        assertThat(entityManagerFactory.isOpen).isTrue()
        assertThat(jdbcTemplate.queryForObject("select timestampdiff(second, utc_timestamp(), current_timestamp())", Int::class.java))
            .isZero()
    }
}
