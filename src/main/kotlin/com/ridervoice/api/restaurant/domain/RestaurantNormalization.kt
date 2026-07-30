package com.ridervoice.api.restaurant.domain

import java.text.Normalizer
import java.util.Locale

internal object RestaurantNormalization {

    private val repeatedWhitespace = Regex("[\\s\\p{Z}]+")

    fun displayText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(repeatedWhitespace, " ")
            .trim()

    fun normalizedText(value: String): String = displayText(value).lowercase(Locale.ROOT)

    fun optionalDisplayText(value: String?): String? =
        value?.let(::displayText)?.takeIf(String::isNotEmpty)

    fun locationKey(normalizedAddress: String, normalizedDetailAddress: String?): String {
        val detail = normalizedDetailAddress.orEmpty()
        return "${normalizedAddress.length}:$normalizedAddress|${detail.length}:$detail"
    }
}
