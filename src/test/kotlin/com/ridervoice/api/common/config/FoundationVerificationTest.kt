package com.ridervoice.api.common.config

import com.ridervoice.api.auth.application.AuthService
import com.ridervoice.api.auth.presentation.AuthController
import com.ridervoice.api.auth.presentation.UserController
import com.ridervoice.api.common.security.OpaqueAccessTokenAuthenticationFilter
import com.ridervoice.api.common.security.SecurityConfig
import com.ridervoice.api.common.security.SecurityProblemHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(classes = [FoundationVerificationTest.TestApplication::class])
class FoundationVerificationTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `OpenAPI publishes API v1 endpoints and the opaque bearer scheme`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/auth/consents'].post") { exists() }
            jsonPath("$.paths['/api/v1/users/me'].get") { exists() }
            jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth") { isArray() }
            jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat") { value("opaque") }
        }
    }

    @Test
    fun `Swagger UI entry point is public`() {
        mockMvc.get("/swagger-ui.html").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/swagger-ui/index.html")
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
        ],
    )
    @Import(
        AuthController::class,
        UserController::class,
        OpenApiConfiguration::class,
        SecurityConfig::class,
        OpaqueAccessTokenAuthenticationFilter::class,
        SecurityProblemHandler::class,
    )
    class TestApplication {
        @Bean
        fun authService(): AuthService = mock(AuthService::class.java)
    }
}
