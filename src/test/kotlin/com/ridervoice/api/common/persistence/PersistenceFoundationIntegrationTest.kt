package com.ridervoice.api.common.persistence

import com.ridervoice.api.support.MySqlIntegrationTest
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class PersistenceFoundationIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `hibernate schema update creates identity tables and JPA boots with a UTC session`() {
        val domainTableCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                'users', 'oauth_accounts', 'oauth_login_states',
                'user_sessions', 'onboarding_tokens'
              )
            """.trimIndent(),
            Int::class.java,
        )
        val identityColumnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND column_name = 'id'
              AND table_name IN (
                'users', 'oauth_accounts', 'oauth_login_states',
                'user_sessions', 'onboarding_tokens'
              )
              AND data_type = 'bigint'
              AND extra LIKE '%auto_increment%'
            """.trimIndent(),
            Int::class.java,
        )

        assertThat(domainTableCount).isEqualTo(5)
        assertThat(identityColumnCount).isEqualTo(5)
        assertThat(entityManagerFactory.isOpen).isTrue()
        assertThat(jdbcTemplate.queryForObject("select timestampdiff(second, utc_timestamp(), current_timestamp())", Int::class.java))
            .isZero()
    }
}
