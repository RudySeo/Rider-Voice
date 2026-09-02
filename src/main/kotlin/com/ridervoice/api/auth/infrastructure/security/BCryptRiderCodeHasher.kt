package com.ridervoice.api.auth.infrastructure.security

import com.ridervoice.api.auth.application.port.out.RiderCodeHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptRiderCodeHasher : RiderCodeHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(rawCode: String): String = requireNotNull(encoder.encode(rawCode))
    override fun matches(rawCode: String, encodedCode: String): Boolean = encoder.matches(rawCode, encodedCode)
}
