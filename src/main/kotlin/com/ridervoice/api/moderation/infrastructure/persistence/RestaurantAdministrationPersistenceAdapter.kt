package com.ridervoice.api.moderation.infrastructure.persistence

import com.ridervoice.api.moderation.application.port.out.RestaurantAdministrationRepository
import com.ridervoice.api.moderation.application.port.out.RestaurantPickupRelinkPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantRenamePersistenceCommand
import com.ridervoice.api.moderation.application.port.out.RestaurantStatusPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.VerifiedPickupLocationPersistenceCommand
import com.ridervoice.api.moderation.application.port.out.StoredAdminRestaurant
import com.ridervoice.api.restaurant.domain.PickupLocation
import com.ridervoice.api.restaurant.domain.Restaurant
import com.ridervoice.api.restaurant.domain.PickupLocationSource
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

    override fun pickupLocationExists(pickupLocationId: Long): Boolean =
        entityManager.find(PickupLocation::class.java, pickupLocationId) != null

    override fun restaurantNameExistsAtPickupLocation(
        pickupLocationId: Long,
        brandName: String,
        excludedRestaurantId: Long,
    ): Boolean = entityManager.createQuery(
        """
        select count(restaurant)
        from Restaurant restaurant
        where restaurant.pickupLocation.id = :pickupLocationId
          and restaurant.brandName = :brandName
          and restaurant.id <> :excludedRestaurantId
        """.trimIndent(),
        Long::class.javaObjectType,
    )
        .setParameter("pickupLocationId", pickupLocationId)
        .setParameter("brandName", brandName)
        .setParameter("excludedRestaurantId", excludedRestaurantId)
        .singleResult > 0L

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

    private fun Restaurant.toSnapshot() = StoredAdminRestaurant(
        restaurantId = id,
        brandName = brandName,
        pickupLocationId = pickupLocation.id,
        status = status,
    )
}
