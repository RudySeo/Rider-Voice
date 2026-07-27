package com.ridervoice.api.moderation.application

import com.ridervoice.api.common.error.ApiErrorCode
import com.ridervoice.api.common.error.ApiException
import com.ridervoice.api.common.error.ResourceNotFoundException
import com.ridervoice.api.common.error.StateConflictException
import com.ridervoice.api.moderation.application.model.RestaurantMergeResult
import com.ridervoice.api.moderation.application.model.RestaurantPickupRelinkResult
import com.ridervoice.api.moderation.application.model.RestaurantRenameResult
import com.ridervoice.api.moderation.application.model.RestaurantStatusChangeResult
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusCommand
import com.ridervoice.api.moderation.application.port.`in`.ChangeRestaurantStatusUseCase
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.MergeRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantCommand
import com.ridervoice.api.moderation.application.port.`in`.RenameRestaurantUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkRestaurantPickupLocationUseCase
import com.ridervoice.api.moderation.application.port.`in`.RelinkValidatedRestaurantPickupLocationCommand
import com.ridervoice.api.moderation.application.port.`in`.RelinkValidatedRestaurantPickupLocationUseCase
import com.ridervoice.api.moderation.application.port.out.AdminRestaurantReviewState
import com.ridervoice.api.moderation.application.port.out.MergedAuthorReviewState
import com.ridervoice.api.moderation.application.port.out.ModerationAdminRepository
import com.ridervoice.api.moderation.application.port.out.ModerationAuditPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.ModerationAuditRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantAdministrationRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantMergePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantPickupRelinkPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantRenamePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantStatusPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.VerifiedPickupLocationPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurant
import com.ridervoice.api.moderation.domain.ModerationAuditPolicy
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.moderation.domain.RestaurantAdminAction
import com.ridervoice.api.moderation.application.port.`in`.RestaurantStatusAction
import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
internal class RestaurantAdministrationService(
    private val admins: ModerationAdminRepository,
    private val restaurants: RestaurantAdministrationRepository,
    private val audits: ModerationAuditRepository,
    private val clock: Clock,
) : MergeRestaurantUseCase, RelinkRestaurantPickupLocationUseCase, RenameRestaurantUseCase,
    ChangeRestaurantStatusUseCase, RelinkValidatedRestaurantPickupLocationUseCase {

    @Transactional
    override fun merge(command: MergeRestaurantCommand): RestaurantMergeResult {
        requireActiveAdmin(command.adminUserId)
        val restaurantIds = setOf(command.duplicateRestaurantId, command.canonicalRestaurantId)
        val locked = restaurants.findRestaurantsForUpdate(restaurantIds).associateBy { it.restaurantId }
        val duplicate = locked[command.duplicateRestaurantId]
            ?: throw ResourceNotFoundException("Duplicate restaurant was not found")
        val canonical = locked[command.canonicalRestaurantId]
            ?: throw ResourceNotFoundException("Canonical restaurant was not found")
        requireActive(duplicate, "Duplicate restaurant")
        requireActive(canonical, "Canonical restaurant")

        val mergedStates = mergeAuthorStates(restaurants.findReviewStatesForUpdate(restaurantIds))
        val completedAt = clock.instant()
        val saved = restaurants.merge(
            RestaurantMergePersistenceCommand(
                duplicateRestaurantId = duplicate.restaurantId,
                canonicalRestaurantId = canonical.restaurantId,
                authorStates = mergedStates,
            ),
        )
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = command.adminUserId,
                action = ModerationAuditPolicy.actionFor(RestaurantAdminAction.MERGE_DUPLICATE),
                targetType = ModerationTargetType.RESTAURANT,
                targetId = duplicate.restaurantId,
                reason = command.reason,
                beforeState = "duplicate={${restaurantState(duplicate)}}," +
                    "canonical={${restaurantState(canonical)}}",
                afterState = "duplicate={${restaurantState(saved)}}," +
                    "mergedAuthorStates=${mergedStateSummary(mergedStates)}," +
                    "reviewsTransferred=true,externalReferencesTransferred=true,platformsTransferred=true",
                occurredAt = completedAt,
            ),
        )
        return RestaurantMergeResult(
            restaurantId = saved.restaurantId,
            status = saved.status,
            canonicalRestaurantId = requireNotNull(saved.canonicalRestaurantId),
            completedAt = completedAt,
        )
    }

    @Transactional
    override fun relinkPickupLocation(
        command: RelinkRestaurantPickupLocationCommand,
    ): RestaurantPickupRelinkResult {
        requireActiveAdmin(command.adminUserId)
        val restaurant = restaurants.findRestaurantsForUpdate(setOf(command.restaurantId)).singleOrNull()
            ?: throw ResourceNotFoundException("Restaurant was not found")
        requireActive(restaurant, "Restaurant")
        if (!restaurants.pickupLocationExists(command.pickupLocationId)) {
            throw ResourceNotFoundException("Pickup location was not found")
        }
        if (restaurant.pickupLocationId == command.pickupLocationId) {
            throw StateConflictException("Restaurant is already linked to the pickup location")
        }
        if (
            restaurants.restaurantNameExistsAtPickupLocation(
                command.pickupLocationId,
                restaurant.normalizedName,
                restaurant.restaurantId,
            )
        ) {
            throw StateConflictException("The pickup location already has the same restaurant brand")
        }

        val completedAt = clock.instant()
        val saved = restaurants.relinkPickupLocation(
            RestaurantPickupRelinkPersistenceCommand(command.restaurantId, command.pickupLocationId),
        )
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = command.adminUserId,
                action = ModerationAuditPolicy.actionFor(RestaurantAdminAction.RELINK_PICKUP_LOCATION),
                targetType = ModerationTargetType.RESTAURANT,
                targetId = restaurant.restaurantId,
                reason = command.reason,
                beforeState = restaurantState(restaurant),
                afterState = restaurantState(saved),
                occurredAt = completedAt,
            ),
        )
        return RestaurantPickupRelinkResult(saved.restaurantId, saved.pickupLocationId, completedAt)
    }

    @Transactional
    override fun rename(command: RenameRestaurantCommand): RestaurantRenameResult {
        requireActiveAdmin(command.adminUserId)
        val restaurant = restaurants.findRestaurantsForUpdate(setOf(command.restaurantId)).singleOrNull()
            ?: throw ResourceNotFoundException("Restaurant was not found")
        requireActive(restaurant, "Restaurant")
        val displayName = RestaurantNormalization.displayText(command.name)
        val normalizedName = RestaurantNormalization.normalizedText(displayName)
        if (normalizedName == restaurant.normalizedName) {
            throw StateConflictException("Restaurant already has the requested name")
        }
        if (
            restaurants.restaurantNameExistsAtPickupLocation(
                restaurant.pickupLocationId,
                normalizedName,
                restaurant.restaurantId,
            )
        ) {
            throw StateConflictException("The pickup location already has the same restaurant brand")
        }
        val completedAt = clock.instant()
        val saved = restaurants.rename(RestaurantRenamePersistenceCommand(command.restaurantId, displayName))
        appendRestaurantAudit(
            command.adminUserId,
            RestaurantAdminAction.RENAME,
            restaurant,
            saved,
            command.reason,
            completedAt,
        )
        return RestaurantRenameResult(saved.restaurantId, saved.brandName, completedAt)
    }

    @Transactional
    override fun relinkValidatedPickupLocation(
        command: RelinkValidatedRestaurantPickupLocationCommand,
    ): RestaurantPickupRelinkResult {
        requireActiveAdmin(command.adminUserId)
        val pickupLocationId = restaurants.findOrCreateVerifiedPickupLocation(
            VerifiedPickupLocationPersistenceCommand(
                command.standardAddress,
                command.detailAddress,
                command.latitude,
                command.longitude,
            ),
        )
        return relinkLocked(
            command.adminUserId,
            command.restaurantId,
            pickupLocationId,
            command.reason,
        )
    }

    private fun relinkLocked(
        adminUserId: Long,
        restaurantId: Long,
        pickupLocationId: Long,
        reason: String?,
    ): RestaurantPickupRelinkResult {
        val restaurant = restaurants.findRestaurantsForUpdate(setOf(restaurantId)).singleOrNull()
            ?: throw ResourceNotFoundException("Restaurant was not found")
        requireActive(restaurant, "Restaurant")
        if (restaurant.pickupLocationId == pickupLocationId) {
            throw StateConflictException("Restaurant is already linked to the pickup location")
        }
        if (
            restaurants.restaurantNameExistsAtPickupLocation(
                pickupLocationId,
                restaurant.normalizedName,
                restaurant.restaurantId,
            )
        ) {
            throw StateConflictException("The pickup location already has the same restaurant brand")
        }
        val completedAt = clock.instant()
        val saved = restaurants.relinkPickupLocation(
            RestaurantPickupRelinkPersistenceCommand(restaurantId, pickupLocationId),
        )
        appendRestaurantAudit(
            adminUserId,
            RestaurantAdminAction.RELINK_PICKUP_LOCATION,
            restaurant,
            saved,
            reason,
            completedAt,
        )
        return RestaurantPickupRelinkResult(saved.restaurantId, saved.pickupLocationId, completedAt)
    }

    @Transactional
    override fun changeStatus(command: ChangeRestaurantStatusCommand): RestaurantStatusChangeResult {
        requireActiveAdmin(command.adminUserId)
        val restaurant = restaurants.findRestaurantsForUpdate(setOf(command.restaurantId)).singleOrNull()
            ?: throw ResourceNotFoundException("Restaurant was not found")
        val (expected, next, action) = when (command.action) {
            RestaurantStatusAction.CLOSE -> Triple(
                RestaurantStatus.ACTIVE,
                RestaurantStatus.CLOSED,
                RestaurantAdminAction.CLOSE,
            )
            RestaurantStatusAction.REOPEN -> Triple(
                RestaurantStatus.CLOSED,
                RestaurantStatus.ACTIVE,
                RestaurantAdminAction.REOPEN,
            )
        }
        if (restaurant.status != expected) {
            throw StateConflictException("Restaurant cannot receive the requested status transition")
        }
        val completedAt = clock.instant()
        val saved = restaurants.changeStatus(RestaurantStatusPersistenceCommand(command.restaurantId, next))
        appendRestaurantAudit(command.adminUserId, action, restaurant, saved, command.reason, completedAt)
        return RestaurantStatusChangeResult(saved.restaurantId, saved.status, completedAt)
    }

    private fun appendRestaurantAudit(
        actorUserId: Long,
        action: RestaurantAdminAction,
        before: StoredAdminRestaurant,
        after: StoredAdminRestaurant,
        reason: String?,
        occurredAt: java.time.Instant,
    ) {
        audits.append(
            ModerationAuditPersistenceCommand(
                actorUserId = actorUserId,
                action = ModerationAuditPolicy.actionFor(action),
                targetType = ModerationTargetType.RESTAURANT,
                targetId = before.restaurantId,
                reason = reason,
                beforeState = restaurantState(before),
                afterState = restaurantState(after),
                occurredAt = occurredAt,
            ),
        )
    }

    private fun mergeAuthorStates(states: List<AdminRestaurantReviewState>): List<MergedAuthorReviewState> =
        states.groupBy { it.authorUserId }
            .map { (authorUserId, authorStates) ->
                val currentReviewId = authorStates
                    .asSequence()
                    .filter { it.currentReviewVisibilityStatus == ReviewVisibilityStatus.ACTIVE }
                    .filter { it.currentReviewId != null && it.currentReviewCreatedAt != null }
                    .maxWithOrNull(
                        compareBy<AdminRestaurantReviewState> { it.currentReviewCreatedAt }
                            .thenBy { it.currentReviewId },
                    )
                    ?.currentReviewId
                MergedAuthorReviewState(
                    authorUserId = authorUserId,
                    lastSubmittedAt = authorStates.maxOf { it.lastSubmittedAt },
                    lastSequence = authorStates.maxOf { it.lastSequence },
                    currentReviewId = currentReviewId,
                )
            }
            .sortedBy { it.authorUserId }

    private fun requireActive(restaurant: StoredAdminRestaurant, label: String) {
        if (restaurant.status != RestaurantStatus.ACTIVE) {
            throw StateConflictException("$label is not active")
        }
    }

    private fun requireActiveAdmin(userId: Long) {
        if (!admins.isActiveAdmin(userId)) {
            throw ApiException(ApiErrorCode.ACCESS_DENIED, "Active administrator role is required")
        }
    }

    private fun restaurantState(restaurant: StoredAdminRestaurant): String =
        "restaurantId=${restaurant.restaurantId},name=${restaurant.brandName},status=${restaurant.status}," +
            "pickupLocationId=${restaurant.pickupLocationId}," +
            "canonicalRestaurantId=${restaurant.canonicalRestaurantId}"

    private fun mergedStateSummary(states: List<MergedAuthorReviewState>): String = states.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ";",
    ) {
        "authorUserId=${it.authorUserId},lastSubmittedAt=${it.lastSubmittedAt}," +
            "lastSequence=${it.lastSequence},currentReviewId=${it.currentReviewId}"
    }
}
