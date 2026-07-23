package com.ridervoice.api.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.method.MethodValidationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI
import java.util.NoSuchElementException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(exception.errorCode, request)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        MethodValidationException::class,
        ConstraintViolationException::class,
    )
    fun handleValidation(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.VALIDATION_FAILED, request)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        ServletRequestBindingException::class,
        IllegalArgumentException::class,
    )
    fun handleBadRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.BAD_REQUEST, request)

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(exception: AuthenticationException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.AUTHENTICATION_REQUIRED, request)

    @ExceptionHandler(NoResourceFoundException::class, NoSuchElementException::class)
    fun handleNotFound(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.RESOURCE_NOT_FOUND, request)

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(exception: IllegalStateException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.STATE_CONFLICT, request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        response(ApiErrorCode.INTERNAL_ERROR, request)

    private fun response(error: ApiErrorCode, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(error.status, error.detail).apply {
            type = error.type
            title = error.title
            instance = URI.create(request.requestURI)
            setProperty("code", error.name)
        }

        return ResponseEntity
            .status(error.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
