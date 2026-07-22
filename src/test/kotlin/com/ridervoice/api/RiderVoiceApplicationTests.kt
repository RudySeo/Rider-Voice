package com.ridervoice.api

import com.ridervoice.api.support.MySqlIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RiderVoiceApplicationTests : MySqlIntegrationTest() {

    @Test
    fun contextLoads() {
    }
}
