package com.ridervoice.api.moderation.application.port.out

import com.ridervoice.api.restaurant.domain.RestaurantStatus
import com.ridervoice.api.review.domain.ReviewVisibilityStatus
import java.time.Instant

interface RestaurantAdministrationRepository {
    fun findRestaurantsForUpdate(restaurantIds: Set<Long>): List<StoredAdminRestaurant>

    fun findReviewStatesForUpdate(restaurantIds: Set<Long>): List<AdminRestaurantReviewState>

    fun pickupLocationExists(pickupLocationId: Long): Boolean

    fun restaurantNameExistsAtPickupLocation(
        pickupLocationId: Long,
        normalizedName: String,
        excludedRestaurantId: Long,
    ): Boolean

    fun merge(command: RestaurantMergePersistenceCommand): StoredAdminRestaurant

    fun relinkPickupLocation(command: RestaurantPickupRelinkPersistenceCommand): StoredAdminRestaurant
    fun rename(command: RestaurantRenamePersistenceCommand): StoredAdminRestaurant
    fun changeStatus(command: RestaurantStatusPersistenceCommand): StoredAdminRestaurant
    fun findOrCreateVerifiedPickupLocation(command: VerifiedPickupLocationPersistenceCommand): Long
}

data class StoredAdminRestaurant(
    val restaurantId: Long,
    val brandName: String,
    val normalizedName: String,
    val pickupLocationId: Long,
    val status: RestaurantStatus,
    val canonicalRestaurantId: Long?,
)

data class AdminRestaurantReviewState(
    val authorUserId: Long,
    val restaurantId: Long,
    val lastSubmittedAt: Instant,
    val lastSequence: Long,
    val currentReviewId: Long?,
    val currentReviewCreatedAt: Instant?,
    val currentReviewVisibilityStatus: ReviewVisibilityStatus?,
)

data class MergedAuthorReviewState(
    val authorUserId: Long,
    val lastSubmittedAt: Instant,
    val lastSequence: Long,
    val currentReviewId: Long?,
)

data class RestaurantMergePersistenceCommand(
    val duplicateRestaurantId: Long,
    val canonicalRestaurantId: Long,
    val authorStates: List<MergedAuthorReviewState>,
    val transferReviews: Boolean = true,
    val transferExternalReferences: Boolean = true,
    val transferPlatforms: Boolean = true,
)

data class RestaurantPickupRelinkPersistenceCommand(
    val restaurantId: Long,
    val pickupLocationId: Long,
)

data class RestaurantRenamePersistenceCommand(
    val restaurantId: Long,
    val name: String,
)

data class RestaurantStatusPersistenceCommand(
    val restaurantId: Long,
    val status: RestaurantStatus,
)

data class VerifiedPickupLocationPersistenceCommand(
    val standardAddress: String,
    val detailAddress: String?,
    val latitude: java.math.BigDecimal,
    val longitude: java.math.BigDecimal,
)
