package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.moderation.application.port.out.AdminRestaurantReview
import com.ridervoice.api.moderation.application.port.out.RestaurantAdministrationRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantMergePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantPickupRelinkPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantRenamePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantStatusPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.VerifiedPickupLocationPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurant
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.RestaurantExternalReference
import com.ridervoice.api.restaurant.domain.RestaurantPlatform
import com.ridervoice.api.restaurant.domain.PickupLocationSource
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

    override fun findReviewsForUpdate(
        restaurantIds: Set<Long>,
    ): List<AdminRestaurantReview> {
        if (restaurantIds.isEmpty()) return emptyList()
        return lockedReviews(restaurantIds).map { review ->
            AdminRestaurantReview(
                reviewId = review.id,
                authorUserId = review.author.id,
                restaurantId = review.restaurant.id,
                submittedAt = review.createdAt,
                active = review.isActive,
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
        val reviews = lockedReviews(restaurantIds)

        if (command.transferReviews) {
            reviews.filter { it.isActive && it.id !in command.activeReviewIds }.forEach(Review::supersede)
            entityManager.flush()
            reviews.filter { it.restaurant.id == duplicate.id }.forEach { it.relinkToRestaurant(canonical) }
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

    override fun rename(command: RestaurantRenamePersistenceCommand): StoredAdminRestaurant {
        val restaurant = lockedRestaurants(setOf(command.restaurantId)).singleOrNull()
            ?: error("Locked restaurant disappeared")
        restaurant.rename(command.name)
        entityManager.flush()
        return restaurant.toSnapshot()
    }

    override fun changeStatus(command: RestaurantStatusPersistenceCommand): StoredAdminRestaurant {
        val restaurant = lockedRestaurants(setOf(command.restaurantId)).singleOrNull()
            ?: error("Locked restaurant disappeared")
        when (command.status) {
            com.ridervoice.api.restaurant.domain.RestaurantStatus.ACTIVE -> restaurant.reopen()
            com.ridervoice.api.restaurant.domain.RestaurantStatus.CLOSED -> restaurant.close()
            com.ridervoice.api.restaurant.domain.RestaurantStatus.MERGED ->
                error("Merged status must be set through merge")
        }
        entityManager.flush()
        return restaurant.toSnapshot()
    }

    override fun findOrCreateVerifiedPickupLocation(command: VerifiedPickupLocationPersistenceCommand): Long {
        val candidate = PickupLocation(
            command.standardAddress,
            command.detailAddress,
            command.latitude,
            command.longitude,
            PickupLocationSource.ADMIN_CORRECTION,
        )
        val existing = entityManager.createQuery(
            "select location from PickupLocation location where location.locationKey = :locationKey",
            PickupLocation::class.java,
        ).setParameter("locationKey", candidate.locationKey).resultList.singleOrNull()
        if (existing != null) return existing.id
        entityManager.persist(candidate)
        entityManager.flush()
        return candidate.id
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

    private fun lockedReviews(restaurantIds: Set<Long>): List<Review> =
        entityManager.createQuery(
            """
            select review
            from Review review
            join fetch review.author
            where review.restaurant.id in :restaurantIds
            order by review.author.id, review.createdAt, review.id
            """.trimIndent(),
            Review::class.java,
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

    private fun Restaurant.toSnapshot() = StoredAdminRestaurant(
        restaurantId = id,
        brandName = brandName,
        normalizedName = normalizedName,
        pickupLocationId = pickupLocation.id,
        status = status,
        canonicalRestaurantId = canonicalRestaurant?.id,
    )
}
