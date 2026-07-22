package com.ridervoice.api.support

import org.junit.jupiter.api.Tag
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class MySqlIntegrationTest
