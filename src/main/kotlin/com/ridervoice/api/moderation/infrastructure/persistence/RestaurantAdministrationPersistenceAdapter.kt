package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.auth.domain.User
import com.ridervoice.api.moderation.application.port.out.AdminRestaurantReviewState
import com.ridervoice.api.moderation.application.port.out.RestaurantAdministrationRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantMergePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantPickupRelinkPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurant
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.review.domain.AuthorRestaurantReviewState
import com.ridervoice.api.review.domain.Review
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Component

@Component
internal class RestaurantAdministrationPersistenceAdapter(
    private val entityManager: EntityManager,
) : RestaurantAdministrationRepository {

    override fun findRestaurantsForUpdate(restaurantIds: Set<Long>): List<StoredAdminRestaurant> {
        if (restaurantIds.isEmpty()) return emptyList()
        return lockedRestaurants(restaurantIds).map { it.toSnapshot() }
    }

    override fun findReviewStatesForUpdate(
        restaurantIds: Set<Long>,
    ): List<AdminRestaurantReviewState> {
        if (restaurantIds.isEmpty()) return emptyList()
        return lockedStates(restaurantIds).map { state ->
            AdminRestaurantReviewState(
                authorUserId = state.author.id,
                restaurantId = state.restaurant.id,
                lastSubmittedAt = state.lastSubmittedAt,
                lastSequence = state.lastSequence,
                currentReviewId = state.currentReview?.id,
                currentReviewCreatedAt = state.currentReview?.createdAt,
                currentReviewVisibilityStatus = state.currentReview?.visibilityStatus,
            )
        }
    }

    override fun pickupLocationExists(pickupLocationId: Long): Boolean =
        entityManager.find(PickupLocation::class.java, pickupLocationId) != null

    override fun restaurantNameExistsAtPickupLocation(
        pickupLocationId: Long,
        normalizedName: String,
        excludedRestaurantId: Long,
    ): Boolean = entityManager.createQuery(
        """
        select count(restaurant)
        from Restaurant restaurant
        where restaurant.pickupLocation.id = :pickupLocationId
          and restaurant.normalizedName = :normalizedName
          and restaurant.id <> :excludedRestaurantId
        """.trimIndent(),
        Long::class.javaObjectType,
    )
        .setParameter("pickupLocationId", pickupLocationId)
        .setParameter("normalizedName", normalizedName)
        .setParameter("excludedRestaurantId", excludedRestaurantId)
        .singleResult > 0L

    override fun merge(command: RestaurantMergePersistenceCommand): StoredAdminRestaurant {
        val restaurants = lockedRestaurants(
            setOf(command.duplicateRestaurantId, command.canonicalRestaurantId),
        ).associateBy { it.id }
        val duplicate = requireNotNull(restaurants[command.duplicateRestaurantId]) {
            "Locked duplicate restaurant disappeared"
        }
        val canonical = requireNotNull(restaurants[command.canonicalRestaurantId]) {
            "Locked canonical restaurant disappeared"
        }
        val restaurantIds = restaurants.keys
        val states = lockedStates(restaurantIds)

        if (command.transferReviews) {
            entityManager.createQuery(
                "select review from Review review where review.restaurant.id = :restaurantId",
                Review::class.java,
            )
                .setParameter("restaurantId", duplicate.id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .resultList
                .forEach { it.relinkToRestaurant(canonical) }
        }
        if (command.transferExternalReferences) {
            entityManager.createQuery(
                "select reference from RestaurantExternalReference reference " +
                    "where reference.restaurant.id = :restaurantId",
                RestaurantExternalReference::class.java,
            )
                .setParameter("restaurantId", duplicate.id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .resultList
                .forEach { it.relinkToRestaurant(canonical) }
        }
        if (command.transferPlatforms) {
            transferPlatforms(duplicate, canonical)
        }

        replaceReviewStates(states, canonical, command)
        duplicate.mergeInto(canonical)
        entityManager.flush()
        return duplicate.toSnapshot()
    }

    override fun relinkPickupLocation(
        command: RestaurantPickupRelinkPersistenceCommand,
    ): StoredAdminRestaurant {
        val restaurant = lockedRestaurants(setOf(command.restaurantId)).singleOrNull()
            ?: error("Locked restaurant disappeared")
        val pickupLocation = entityManager.find(
            PickupLocation::class.java,
            command.pickupLocationId,
            LockModeType.PESSIMISTIC_READ,
        ) ?: error("Pickup location disappeared")
        restaurant.relinkPickupLocation(pickupLocation)
        entityManager.flush()
        return restaurant.toSnapshot()
    }

    private fun lockedRestaurants(restaurantIds: Set<Long>): List<Restaurant> =
        entityManager.createQuery(
            "select restaurant from Restaurant restaurant " +
                "where restaurant.id in :restaurantIds order by restaurant.id",
            Restaurant::class.java,
        )
            .setParameter("restaurantIds", restaurantIds)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

    private fun lockedStates(restaurantIds: Set<Long>): List<AuthorRestaurantReviewState> =
        entityManager.createQuery(
            """
            select state
            from AuthorRestaurantReviewState state
            left join fetch state.currentReview
            where state.restaurant.id in :restaurantIds
            order by state.author.id, state.restaurant.id
            """.trimIndent(),
            AuthorRestaurantReviewState::class.java,
        )
            .setParameter("restaurantIds", restaurantIds)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

    private fun transferPlatforms(duplicate: Restaurant, canonical: Restaurant) {
        val canonicalPlatforms = entityManager.createQuery(
            "select link from RestaurantPlatform link where link.restaurant.id = :restaurantId",
            RestaurantPlatform::class.java,
        )
            .setParameter("restaurantId", canonical.id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .associateBy { it.platform }
        val duplicatePlatforms = entityManager.createQuery(
            "select link from RestaurantPlatform link where link.restaurant.id = :restaurantId",
            RestaurantPlatform::class.java,
        )
            .setParameter("restaurantId", duplicate.id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

        duplicatePlatforms.forEach { link ->
            if (link.platform in canonicalPlatforms) {
                entityManager.remove(link)
            } else {
                link.relinkToRestaurant(canonical)
            }
        }
    }

    private fun replaceReviewStates(
        existingStates: List<AuthorRestaurantReviewState>,
        canonical: Restaurant,
        command: RestaurantMergePersistenceCommand,
    ) {
        val desiredByAuthor = command.authorStates.associateBy { it.authorUserId }
        val statesByAuthor = existingStates.groupBy { it.author.id }
        check(statesByAuthor.keys == desiredByAuthor.keys) {
            "Merge review state plan does not match locked database state"
        }

        statesByAuthor.forEach { (authorUserId, authorStates) ->
            val desired = requireNotNull(desiredByAuthor[authorUserId])
            val canonicalState = authorStates.firstOrNull { it.restaurant.id == canonical.id }
            val duplicateStates = authorStates.filterNot { it === canonicalState }
            val currentReview = desired.currentReviewId?.let {
                entityManager.getReference(Review::class.java, it)
            }
            if (canonicalState != null) {
                canonicalState.synchronize(
                    desired.lastSubmittedAt,
                    desired.lastSequence,
                    currentReview,
                )
                duplicateStates.forEach(entityManager::remove)
            } else {
                authorStates.forEach(entityManager::remove)
                entityManager.persist(
                    AuthorRestaurantReviewState(
                        author = entityManager.getReference(User::class.java, authorUserId),
                        restaurant = canonical,
                        lastSubmittedAt = desired.lastSubmittedAt,
                        lastSequence = desired.lastSequence,
                        currentReview = currentReview,
                    ),
                )
            }
        }
    }

    private fun Restaurant.toSnapshot() = StoredAdminRestaurant(
        restaurantId = id,
        brandName = brandName,
        normalizedName = normalizedName,
        pickupLocationId = pickupLocation.id,
        status = status,
        canonicalRestaurantId = canonicalRestaurant?.id,
    )
}
