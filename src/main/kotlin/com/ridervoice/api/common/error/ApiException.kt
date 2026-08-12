package com.ridervoice.api.common.error

open class ApiException(
    val errorCode: ApiErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BadRequestException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.BAD_REQUEST, message, cause)

class AuthenticationRequiredException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, message, cause)

class ResourceNotFoundException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, message, cause)

class StateConflictException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.STATE_CONFLICT, message, cause)

class ExternalProviderUnavailableException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, message, cause)

class PublicSearchRateLimitExceededException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.PUBLIC_SEARCH_RATE_LIMIT_EXCEEDED, message, cause)

class ReportRateLimitExceededException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.REPORT_RATE_LIMIT_EXCEEDED, message, cause)
