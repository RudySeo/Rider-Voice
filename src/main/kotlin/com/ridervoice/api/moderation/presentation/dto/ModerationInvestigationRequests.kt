package com.ridervoice.api.moderation.presentation.dto

import com.ridervoice.api.moderation.domain.ModerationAuditAction
import com.ridervoice.api.moderation.domain.ModerationTargetType
import com.ridervoice.api.restaurant.domain.RestaurantStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class AdminRestaurantSearchRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 100)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val query: String,
    val status: RestaurantStatus? = null,
    @field:Schema(nullable = true) val cursor: String? = null,
    @field:Min(1)
    @field:Max(50)
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "50")
    val size: Int = 20,
)

data class ModerationAuditSearchRequest(
    val targetType: ModerationTargetType? = null,
    @field:Positive val targetId: Long? = null,
    @field:Positive val actorUserId: Long? = null,
    val action: ModerationAuditAction? = null,
    @field:Schema(nullable = true) val cursor: String? = null,
    @field:Min(1)
    @field:Max(50)
    @field:Schema(defaultValue = "20", minimum = "1", maximum = "50")
    val size: Int = 20,
)
