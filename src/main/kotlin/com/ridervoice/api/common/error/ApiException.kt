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

class AccessDeniedException(message: String? = null, cause: Throwable? = null) :
    ApiException(ApiErrorCode.ACCESS_DENIED, message, cause)

class RiderVerificationFailedException(message: String? = null) :
    ApiException(ApiErrorCode.RIDER_VERIFICATION_FAILED, message)

class RiderVerificationRateLimitException(
    val retryAfterSeconds: Long,
) : ApiException(ApiErrorCode.RIDER_VERIFICATION_RATE_LIMITED)

class RiderVerificationUnavailableException :
    ApiException(ApiErrorCode.RIDER_VERIFICATION_UNAVAILABLE)

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
