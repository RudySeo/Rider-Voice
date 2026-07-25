package com.ridervoice.api.restaurant.presentation.dto

import com.ridervoice.api.restaurant.domain.RestaurantNormalization
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank

data class SearchQueryRequest(
    @field:NotBlank
    @field:Schema(
        description = "정규화 후 2~100자인 검색어",
        minLength = 2,
        maxLength = 100,
        example = "강남 분식",
    )
    val query: String,
) {
    @get:AssertTrue(message = "정규화한 검색어는 2~100자여야 합니다.")
    @get:Schema(hidden = true)
    val normalizedLengthValid: Boolean
        get() = RestaurantNormalization.displayText(query).length in 2..100
}
