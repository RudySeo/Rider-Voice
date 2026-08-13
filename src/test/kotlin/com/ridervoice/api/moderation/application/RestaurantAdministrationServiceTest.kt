package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusCommand
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkValidatedRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantAdministrationRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantPickupRelinkPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantRenamePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantStatusPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.VerifiedPickupLocationPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurant
import com.ridervoice.api.moderation.application.port.out.StoredModerationAudit
import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestaurantAdministrationServiceTest {

    @Test
    fun `pickup relink preserves restaurant identity and audits old and new locations`() {
        val repository = FakeRestaurantAdministrationRepository(
            restaurants = mutableMapOf(DUPLICATE_ID to restaurant(DUPLICATE_ID, PICKUP_ID)),
            pickupLocationIds = setOf(CANONICAL_PICKUP_ID),
        )
        val audits = FakeAuditRepository()
        val service = service(repository, audits)

        val result = service.relinkPickupLocation(
            RelinkRestaurantPickupLocationCommand(ADMIN_ID, DUPLICATE_ID, CANONICAL_PICKUP_ID, "주소 정정"),
        )

        assertThat(result.restaurantId).isEqualTo(DUPLICATE_ID)
        assertThat(result.pickupLocationId).isEqualTo(CANONICAL_PICKUP_ID)
        assertThat(repository.relinkCommand).isEqualTo(
            RestaurantPickupRelinkPersistenceCommand(DUPLICATE_ID, CANONICAL_PICKUP_ID),
        )
        assertThat(audits.commands.single().action)
            .isEqualTo(ModerationAuditAction.RESTAURANT_PICKUP_RELINKED)
        assertThat(audits.commands.single().beforeState).contains("pickupLocationId=$PICKUP_ID")
        assertThat(audits.commands.single().afterState).contains("pickupLocationId=$CANONICAL_PICKUP_ID")
    }

    @Test
    fun `validated address creates or reuses a pickup location before atomic relink`() {
        val repository = FakeRestaurantAdministrationRepository(
            restaurants = mutableMapOf(DUPLICATE_ID to restaurant(DUPLICATE_ID, PICKUP_ID)),
            pickupLocationIds = setOf(CANONICAL_PICKUP_ID),
        )
        val service = service(repository, FakeAuditRepository())

        val result = service.relinkValidatedPickupLocation(
            RelinkValidatedRestaurantPickupLocationCommand(
                ADMIN_ID,
                DUPLICATE_ID,
                "서울 강남구 새 주소 1",
                "지하 1층",
                java.math.BigDecimal("37.5"),
                java.math.BigDecimal("127.0"),
                "주소 정정",
            ),
        )

        assertThat(repository.verifiedLocationCommand!!.standardAddress).isEqualTo("서울 강남구 새 주소 1")
        assertThat(result.pickupLocationId).isEqualTo(CANONICAL_PICKUP_ID)
    }

    @Test
    fun `rename normalizes the new name and audits the change`() {
        val repository = FakeRestaurantAdministrationRepository(
            restaurants = mutableMapOf(DUPLICATE_ID to restaurant(DUPLICATE_ID, PICKUP_ID)),
        )
        val audits = FakeAuditRepository()
        val service = service(repository, audits)

        val result = service.rename(RenameRestaurantCommand(ADMIN_ID, DUPLICATE_ID, "  새 브랜드  ", "상호 정정"))

        assertThat(result.name).isEqualTo("새 브랜드")
        assertThat(repository.renameCommand).isEqualTo(
            RestaurantRenamePersistenceCommand(DUPLICATE_ID, "새 브랜드"),
        )
        assertThat(audits.commands.single().action).isEqualTo(ModerationAuditAction.RESTAURANT_RENAMED)
    }

    @Test
    fun `rename rejects an unchanged name regardless of unicode whitespace and case`() {
        val repository = FakeRestaurantAdministrationRepository(
            restaurants = mutableMapOf(
                DUPLICATE_ID to restaurant(DUPLICATE_ID, PICKUP_ID).copy(brandName = "Rider Voice"),
            ),
        )
        val service = service(repository, FakeAuditRepository())

        assertThatThrownBy {
            service.rename(RenameRestaurantCommand(ADMIN_ID, DUPLICATE_ID, "  rider　voice  ", null))
        }.isInstanceOf(StateConflictException::class.java)
        assertThat(repository.renameCommand).isNull()
    }

    @Test
    fun `close and reopen preserve identity and audit each status transition`() {
        val repository = FakeRestaurantAdministrationRepository(
            restaurants = mutableMapOf(DUPLICATE_ID to restaurant(DUPLICATE_ID, PICKUP_ID)),
        )
        val audits = FakeAuditRepository()
        val service = service(repository, audits)

        assertThat(
            service.changeStatus(ChangeRestaurantStatusCommand.close(ADMIN_ID, DUPLICATE_ID, "폐업 확인")).status,
        ).isEqualTo(RestaurantStatus.CLOSED)
        assertThat(
            service.changeStatus(ChangeRestaurantStatusCommand.reopen(ADMIN_ID, DUPLICATE_ID, "영업 재개")).status,
        ).isEqualTo(RestaurantStatus.ACTIVE)
        assertThat(audits.commands.map { it.action }).containsExactly(
            ModerationAuditAction.RESTAURANT_CLOSED,
            ModerationAuditAction.RESTAURANT_REOPENED,
        )
    }

    @Test
    fun `service is transactional`() {
        assertThat(
            RestaurantAdministrationService::class.java.getMethod(
                "relinkPickupLocation",
                RelinkRestaurantPickupLocationCommand::class.java,
            ).getAnnotation(Transactional::class.java),
        ).isNotNull
    }

    private fun service(
        repository: RestaurantAdministrationRepository,
        audits: ModerationAuditRepository,
    ) = RestaurantAdministrationService(
        admins = ModerationAdminRepository { true },
        restaurants = repository,
        audits = audits,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun restaurant(
        id: Long,
        pickupLocationId: Long,
        status: RestaurantStatus = RestaurantStatus.ACTIVE,
    ) = StoredAdminRestaurant(id, "브랜드-$id", pickupLocationId, status)

    private class FakeRestaurantAdministrationRepository(
        private val restaurants: MutableMap<Long, StoredAdminRestaurant>,
        private val pickupLocationIds: Set<Long> = emptySet(),
    ) : RestaurantAdministrationRepository {
        var relinkCommand: RestaurantPickupRelinkPersistenceCommand? = null
        var renameCommand: RestaurantRenamePersistenceCommand? = null
        var verifiedLocationCommand: VerifiedPickupLocationPersistenceCommand? = null

        override fun findRestaurantsForUpdate(restaurantIds: Set<Long>): List<StoredAdminRestaurant> =
            restaurantIds.mapNotNull(restaurants::get)

        override fun pickupLocationExists(pickupLocationId: Long): Boolean = pickupLocationId in pickupLocationIds

        override fun restaurantNameExistsAtPickupLocation(
            pickupLocationId: Long,
            brandName: String,
            excludedRestaurantId: Long,
        ): Boolean = false

        override fun relinkPickupLocation(
            command: RestaurantPickupRelinkPersistenceCommand,
        ): StoredAdminRestaurant {
            relinkCommand = command
            val relinked = restaurants.getValue(command.restaurantId).copy(
                pickupLocationId = command.pickupLocationId,
            )
            restaurants[relinked.restaurantId] = relinked
            return relinked
        }

        override fun rename(command: RestaurantRenamePersistenceCommand): StoredAdminRestaurant {
            renameCommand = command
            val renamed = restaurants.getValue(command.restaurantId).copy(
                brandName = command.name,
            )
            restaurants[renamed.restaurantId] = renamed
            return renamed
        }

        override fun changeStatus(command: RestaurantStatusPersistenceCommand): StoredAdminRestaurant {
            val changed = restaurants.getValue(command.restaurantId).copy(status = command.status)
            restaurants[changed.restaurantId] = changed
            return changed
        }

        override fun findOrCreateVerifiedPickupLocation(command: VerifiedPickupLocationPersistenceCommand): Long {
            verifiedLocationCommand = command
            return CANONICAL_PICKUP_ID
        }
    }

    private class FakeAuditRepository : ModerationAuditRepository {
        val commands = mutableListOf<ModerationAuditPersistenceCommand>()

        override fun append(command: ModerationAuditPersistenceCommand): StoredModerationAudit {
            commands += command
            return StoredModerationAudit(
                auditId = commands.size.toLong(),
                actorUserId = command.actorUserId,
                action = command.action,
                targetType = command.targetType,
                targetId = command.targetId,
                reason = command.reason,
                beforeState = command.beforeState,
                afterState = command.afterState,
                occurredAt = command.occurredAt,
                createdAt = command.occurredAt,
            )
        }
    }

    private companion object {
        const val ADMIN_ID = 1L
        const val DUPLICATE_ID = 10L
        const val PICKUP_ID = 100L
        const val CANONICAL_PICKUP_ID = 200L
        val NOW: Instant = Instant.parse("2026-07-26T00:00:00Z")
    }
}
