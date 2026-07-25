package com.ridervoice.api.restaurant.infrastructure.kakao

import com.ridervoice.api.restaurant.application.model.ExternalAddressCandidate
import com.ridervoice.api.restaurant.application.model.ExternalRestaurantCandidate
import com.ridervoice.api.restaurant.application.model.ProviderFailureReason
import com.ridervoice.api.restaurant.application.model.ProviderSearchResult
import com.ridervoice.api.restaurant.application.port.out.KakaoAddressSearchPort
import com.ridervoice.api.restaurant.application.port.out.KakaoKeywordSearchPort
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.net.SocketTimeoutException

class KakaoKeywordSearchAdapter(
    private val client: KakaoLocalClient,
) : KakaoKeywordSearchPort {
    override fun search(query: String, limit: Int): ProviderSearchResult<ExternalRestaurantCandidate> =
        client.searchKeywords(query, limit)
}

class KakaoAddressSearchAdapter(
    private val client: KakaoLocalClient,
) : KakaoAddressSearchPort {
    override fun search(query: String, limit: Int): ProviderSearchResult<ExternalAddressCandidate> =
        client.searchAddresses(query, limit)
}

class KakaoLocalClient(properties: KakaoLocalProperties) {

    private val restClient: RestClient

    init {
        val apiKey = properties.apiKey.trim()
        require(apiKey.isNotEmpty()) { "Kakao Local REST API key must be configured" }

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }
        restClient = RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey")
            .build()
    }

    fun searchKeywords(
        query: String,
        limit: Int,
    ): ProviderSearchResult<ExternalRestaurantCandidate> = execute(
        path = KEYWORD_SEARCH_PATH,
        query = query,
        limit = limit,
        responseType = KakaoKeywordSearchResponse::class.java,
    ) { response -> response.documents.map(KakaoKeywordDocument::toCandidate) }

    fun searchAddresses(
        query: String,
        limit: Int,
    ): ProviderSearchResult<ExternalAddressCandidate> = execute(
        path = ADDRESS_SEARCH_PATH,
        query = query,
        limit = limit,
        responseType = KakaoAddressSearchResponse::class.java,
    ) { response -> response.documents.map(KakaoAddressDocument::toCandidate) }

    private fun <R : Any, T> execute(
        path: String,
        query: String,
        limit: Int,
        responseType: Class<R>,
        map: (R) -> List<T>,
    ): ProviderSearchResult<T> = try {
        val response = restClient.get()
            .uri { builder ->
                builder.path(path)
                    .queryParam("query", query)
                    .queryParam("size", limit)
                    .build()
            }
            .retrieve()
            .body(responseType)
            ?: return ProviderSearchResult.Unavailable(ProviderFailureReason.INVALID_RESPONSE)

        ProviderSearchResult.Available(map(response))
    } catch (exception: RestClientResponseException) {
        ProviderSearchResult.Unavailable(exception.toFailureReason())
    } catch (exception: ResourceAccessException) {
        val reason = if (exception.hasTimeoutCause()) {
            ProviderFailureReason.TIMEOUT
        } else {
            ProviderFailureReason.CONNECTION_FAILED
        }
        ProviderSearchResult.Unavailable(reason)
    } catch (_: InvalidKakaoResponseException) {
        ProviderSearchResult.Unavailable(ProviderFailureReason.INVALID_RESPONSE)
    } catch (_: RestClientException) {
        ProviderSearchResult.Unavailable(ProviderFailureReason.INVALID_RESPONSE)
    }

    private fun RestClientResponseException.toFailureReason(): ProviderFailureReason = when {
        statusCode.value() == 429 -> ProviderFailureReason.RATE_LIMITED
        statusCode.is4xxClientError -> ProviderFailureReason.CLIENT_ERROR
        statusCode.is5xxServerError -> ProviderFailureReason.SERVER_ERROR
        else -> ProviderFailureReason.INVALID_RESPONSE
    }

    private fun ResourceAccessException.hasTimeoutCause(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { cause ->
            cause is HttpTimeoutException || cause is SocketTimeoutException
        }

    private companion object {
        const val KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json"
        const val ADDRESS_SEARCH_PATH = "/v2/local/search/address.json"
    }
}

private data class KakaoKeywordSearchResponse(
    val documents: List<KakaoKeywordDocument>,
)

private data class KakaoKeywordDocument(
    val id: String,
    val place_name: String,
    val address_name: String,
    val road_address_name: String,
    val x: String,
    val y: String,
) {
    fun toCandidate() = ExternalRestaurantCandidate(
        externalPlaceId = id.requiredValue(),
        name = place_name.requiredValue(),
        standardAddress = road_address_name.optionalValue() ?: address_name.requiredValue(),
        lotNumberAddress = address_name.optionalValue(),
        latitude = y.toCoordinate(),
        longitude = x.toCoordinate(),
    )
}

private data class KakaoAddressSearchResponse(
    val documents: List<KakaoAddressDocument>,
)

private data class KakaoAddressDocument(
    val address_name: String,
    val x: String,
    val y: String,
    val address: KakaoAddressDetail?,
    val road_address: KakaoAddressDetail?,
) {
    fun toCandidate() = ExternalAddressCandidate(
        standardAddress = road_address?.address_name.optionalValue()
            ?: address?.address_name.optionalValue()
            ?: address_name.requiredValue(),
        lotNumberAddress = address?.address_name.optionalValue(),
        latitude = y.toCoordinate(),
        longitude = x.toCoordinate(),
    )
}

private data class KakaoAddressDetail(
    val address_name: String,
)

private fun String?.optionalValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String.requiredValue(): String =
    optionalValue() ?: throw InvalidKakaoResponseException()

private fun String.toCoordinate(): BigDecimal = try {
    requiredValue().toBigDecimal()
} catch (_: NumberFormatException) {
    throw InvalidKakaoResponseException()
}

private class InvalidKakaoResponseException : RuntimeException()
