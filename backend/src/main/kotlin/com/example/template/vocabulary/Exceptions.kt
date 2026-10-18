package com.example.template.vocabulary

/**
 * Stable, machine-readable error codes returned in the `code` member of RFC 7807
 * problem responses. The server speaks English only; the frontend localizes by
 * branching on these codes, so they must never be renamed without a frontend change.
 */
enum class ErrorCode {
    NOT_FOUND,
    INVALID_INPUT,
    EMAIL_ALREADY_REGISTERED,
    INVALID_CREDENTIALS,
    UNAUTHENTICATED,
}

/**
 * Base class for domain/application failures that map to an HTTP status in the REST
 * layer. The status itself stays out of the vocabulary layer — the web adapter
 * decides it from the concrete exception type.
 */
open class ApiException(
    val code: ErrorCode,
    message: String,
) : RuntimeException(message)

/** Thrown when a requested entity does not exist. Mapped to HTTP 404. */
class NotFoundException(message: String) : ApiException(ErrorCode.NOT_FOUND, message)

/** Thrown when input fails a domain-level validation rule. Mapped to HTTP 400. */
class InvalidInputException(message: String) : ApiException(ErrorCode.INVALID_INPUT, message)

/** Thrown when a registration clashes with an existing account. Mapped to HTTP 409. */
class EmailAlreadyRegisteredException(message: String) :
    ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, message)

/**
 * Thrown when supplied credentials do not match an account. The message deliberately
 * does not reveal whether the email exists. Mapped to HTTP 401.
 */
class InvalidCredentialsException(message: String) :
    ApiException(ErrorCode.INVALID_CREDENTIALS, message)

/** Thrown when a session is missing, invalid or expired. Mapped to HTTP 401. */
class UnauthenticatedException(message: String) :
    ApiException(ErrorCode.UNAUTHENTICATED, message)
