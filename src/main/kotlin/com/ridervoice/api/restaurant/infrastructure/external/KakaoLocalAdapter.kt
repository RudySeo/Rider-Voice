package com.ridervoice.api.restaurant.infrastructure.external

import com.fasterxml.jackson.annotation.JsonProperty
import com.ridervoice.api.restaurant.application.model.PlaceCandidate
import com.ridervoice.api.restaurant.application.port.out.KakaoLocalPort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException

internal enum class KakaoLocalFailure {
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    INVALID_RESPONSE,
    CONNECTION_ERROR,
}

internal class KakaoLocalProviderException(
    val failure: KakaoLocalFailure,
) : RuntimeException("Kakao Local request failed: ${failure.name}")

@Component
@EnableConfigurationProperties(KakaoLocalProperties::class)
internal class KakaoLocalAdapter(
    private val properties: KakaoLocalProperties,
) : KakaoLocalPort {

    private val client = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(requestFactory())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK ${properties.restApiKey}")
        .build()

    override fun searchByKeyword(query: String): List<PlaceCandidate> = try {
        val response = client.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(KEYWORD_SEARCH_PATH)
                    .queryParam("query", query)
                    .queryParam("category_group_code", RESTAURANT_CATEGORY_GROUP_CODE)
                    .build()
            }
            .retrieve()
            .body(KakaoKeywordSearchResponse::class.java)
            ?: throw KakaoLocalProviderException(KakaoLocalFailure.INVALID_RESPONSE)

        response.documents
            .asSequence()
            .filter { it.categoryGroupCode == RESTAURANT_CATEGORY_GROUP_CODE }
            .map { it.toCandidate() }
            .toList()
    } catch (exception: KakaoLocalProviderException) {
        throw exception
    } catch (exception: RestClientResponseException) {
        throw KakaoLocalProviderException(exception.statusCode.toFailure())
    } catch (exception: ResourceAccessException) {
        val failure = if (exception.hasTimeoutCause()) {
            KakaoLocalFailure.TIMEOUT
        } else {
            KakaoLocalFailure.CONNECTION_ERROR
        }
        throw KakaoLocalProviderException(failure)
    } catch (exception: RestClientException) {
        throw KakaoLocalProviderException(KakaoLocalFailure.INVALID_RESPONSE)
    } catch (exception: NumberFormatException) {
        throw KakaoLocalProviderException(KakaoLocalFailure.INVALID_RESPONSE)
    }

    private fun requestFactory(): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.timeout)
            .build()

        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.timeout)
        }
    }

    private fun KakaoPlaceDocument.toCandidate() = PlaceCandidate(
        kakaoPlaceId = id,
        name = placeName,
        address = roadAddressName.ifBlank { addressName },
        latitude = BigDecimal(y),
        longitude = BigDecimal(x),
    )

    private fun org.springframework.http.HttpStatusCode.toFailure(): KakaoLocalFailure = when {
        value() == 429 -> KakaoLocalFailure.RATE_LIMITED
        is4xxClientError -> KakaoLocalFailure.CLIENT_ERROR
        else -> KakaoLocalFailure.SERVER_ERROR
    }

    private fun Throwable.hasTimeoutCause(): Boolean = generateSequence(this) { it.cause }
        .any { it is HttpTimeoutException || it is SocketTimeoutException }

    private data class KakaoKeywordSearchResponse(
        val documents: List<KakaoPlaceDocument>,
    )

    private data class KakaoPlaceDocument(
        val id: String,
        @JsonProperty("place_name") val placeName: String,
        @JsonProperty("address_name") val addressName: String,
        @JsonProperty("road_address_name") val roadAddressName: String,
        @JsonProperty("category_group_code") val categoryGroupCode: String,
        val x: String,
        val y: String,
    )

    private companion object {
        const val KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json"
        const val RESTAURANT_CATEGORY_GROUP_CODE = "FD6"
    }
}
