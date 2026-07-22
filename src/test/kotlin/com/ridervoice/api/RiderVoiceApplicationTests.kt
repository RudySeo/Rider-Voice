package com.ridervoice.api

import com.ridervoice.api.support.PostgreSqlIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RiderVoiceApplicationTests : PostgreSqlIntegrationTest() {

    @Test
    fun contextLoads() {
    }
}
