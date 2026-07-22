package com.ridervoice.api.auth.presentation

import com.ridervoice.api.auth.application.AuthService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ConsentRequest(@field:NotBlank val termsVersion: String)
data class TokenRequest(@field:NotBlank val refreshToken: String)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {
    @GetMapping("/kakao/authorize") fun authorize() = mapOf("authorizationUrl" to auth.authorize())
    @GetMapping("/kakao/callback") fun callback(@RequestParam code: String, @RequestParam state: String) = auth.callback(code, state)
    @PostMapping("/consents") fun consent(@RequestHeader("Authorization") authorization: String, @Valid @RequestBody request: ConsentRequest) = auth.agree(auth.userIdFor(authorization.removePrefix("Bearer ")), request.termsVersion)
    @PostMapping("/refresh") fun refresh(@Valid @RequestBody request: TokenRequest) = auth.refresh(request.refreshToken)
    @PostMapping("/logout") fun logout(@Valid @RequestBody request: TokenRequest): ResponseEntity<Void> { auth.logout(request.refreshToken); return ResponseEntity.noContent().build() }
}

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val auth: AuthService) {
    @GetMapping("/me") fun me(@RequestHeader("Authorization") authorization: String) = auth.me(authorization.removePrefix("Bearer "))
}
