package com.ridervoice.api.auth.application

import com.ridervoice.api.auth.application.port.`in`.RotateRiderInviteCodeCommand
import com.ridervoice.api.auth.application.port.`in`.VerifyRiderCommand
import com.ridervoice.api.auth.application.port.out.RiderCodeHasher
import com.ridervoice.api.auth.application.port.out.RiderInviteCodeStore
import com.ridervoice.api.auth.application.port.out.RiderVerificationAttemptStore
import com.ridervoice.api.auth.application.port.out.UserStore
import com.ridervoice.api.auth.domain.RiderInviteCode
import com.ridervoice.api.auth.domain.RiderVerificationAttempt
import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.auth.domain.UserRole
import com.ridervoice.api.common.error.AccessDeniedException
import com.ridervoice.api.common.error.RiderVerificationFailedException
import com.ridervoice.api.common.error.RiderVerificationRateLimitException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.transaction.annotation.Transactional

class RiderAccessServiceTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z")

    @Test
    fun `matching code promotes a user and clears failed attempts`() {
        val fixture = fixture()
        fixture.attempts.value = RiderVerificationAttempt(fixture.user).also { it.registerFailure(now) }

        val result = fixture.service.verify(VerifyRiderCommand(7L, "123456"))

        assertThat(result.role).isEqualTo(UserRole.RIDER)
        assertThat(fixture.attempts.value?.failedAttemptCount).isZero()
    }

    @Test
    fun `fifth mismatch locks only that account`() {
        val fixture = fixture(matches = false)
        repeat(4) {
            assertThatThrownBy { fixture.service.verify(VerifyRiderCommand(7L, "000000")) }
                .isInstanceOf(RiderVerificationFailedException::class.java)
        }

        assertThatThrownBy { fixture.service.verify(VerifyRiderCommand(7L, "000000")) }
            .isInstanceOf(RiderVerificationRateLimitException::class.java)
            .extracting("retryAfterSeconds")
            .isEqualTo(900L)
    }

    @Test
    fun `admin rotation revokes the old code and stores only a hash`() {
        val fixture = fixture(userRole = UserRole.ADMIN)

        fixture.service.rotate(RotateRiderInviteCodeCommand(7L, "654321"))

        assertThat(fixture.codes.current?.codeHash).isEqualTo("hash:654321")
        assertThat(fixture.codes.current?.codeHash).doesNotContain("123456")
        assertThat(fixture.codes.revoked).hasSize(1)
    }

    @Test
    fun `non admin cannot rotate the shared code`() {
        val fixture = fixture()

        assertThatThrownBy { fixture.service.rotate(RotateRiderInviteCodeCommand(7L, "654321")) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `failed verification exceptions do not roll back persisted attempt counters`() {
        val annotation = RiderAccessService::class.java
            .getMethod("verify", VerifyRiderCommand::class.java)
            .getAnnotation(Transactional::class.java)

        assertThat(annotation.noRollbackFor.toSet()).contains(
            RiderVerificationFailedException::class,
            RiderVerificationRateLimitException::class,
        )
    }

    private fun fixture(matches: Boolean = true, userRole: UserRole = UserRole.USER): Fixture {
        val user = User().also { it.id = 7L; setRole(it, userRole) }
        val users = FakeUserStore(user)
        val codes = FakeCodeStore(RiderInviteCode(User().also { it.id = 99L; setRole(it, UserRole.ADMIN) }, "hash:123456"))
        val attempts = FakeAttemptStore()
        val hasher = object : RiderCodeHasher {
            override fun hash(rawCode: String) = "hash:$rawCode"
            override fun matches(rawCode: String, encodedCode: String) = matches && encodedCode == "hash:123456"
        }
        return Fixture(
            RiderAccessService(users, codes, attempts, hasher, Clock.fixed(now, ZoneOffset.UTC)),
            user, codes, attempts,
        )
    }

    private data class Fixture(
        val service: RiderAccessService,
        val user: User,
        val codes: FakeCodeStore,
        val attempts: FakeAttemptStore,
    )

    private class FakeUserStore(private val user: User) : UserStore {
        override fun findUser(userId: Long) = user.takeIf { it.id == userId }
        override fun findUserForUpdate(userId: Long) = findUser(userId)
        override fun saveUser(user: User) = user
    }

    private class FakeCodeStore(initial: RiderInviteCode?) : RiderInviteCodeStore {
        var current = initial
        val revoked = mutableListOf<RiderInviteCode>()
        override fun findCurrentForUpdate() = current
        override fun saveCode(code: RiderInviteCode): RiderInviteCode {
            if (!code.isCurrent) revoked += code else current = code
            return code
        }
    }

    private class FakeAttemptStore : RiderVerificationAttemptStore {
        var value: RiderVerificationAttempt? = null
        override fun findByUserIdForUpdate(userId: Long) = value
        override fun saveAttempt(attempt: RiderVerificationAttempt) = attempt.also { value = it }
    }

    private fun setRole(user: User, role: UserRole) {
        User::class.java.getDeclaredField("role").also { it.isAccessible = true; it.set(user, role) }
    }
}
