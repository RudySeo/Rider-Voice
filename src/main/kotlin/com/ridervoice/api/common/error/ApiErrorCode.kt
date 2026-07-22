package com.ridervoice.api.common.error

import org.springframework.http.HttpStatus
import java.net.URI

enum class ApiErrorCode(
    val status: HttpStatus,
    val title: String,
    val detail: String,
) {
    VALIDATION_FAILED(
        HttpStatus.BAD_REQUEST,
        "Validation failed",
        "Request validation failed.",
    ),
    BAD_REQUEST(
        HttpStatus.BAD_REQUEST,
        "Bad request",
        "The request is invalid.",
    ),
    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
        "Authentication required",
        "Authentication is required.",
    ),
    RESOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Resource not found",
        "The requested resource was not found.",
    ),
    STATE_CONFLICT(
        HttpStatus.CONFLICT,
        "State conflict",
        "The request conflicts with the current resource state.",
    ),
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "An unexpected error occurred.",
    ),
    ;

    val type: URI = URI.create("urn:ridervoice:error:${name.lowercase().replace('_', '-')}")
}
