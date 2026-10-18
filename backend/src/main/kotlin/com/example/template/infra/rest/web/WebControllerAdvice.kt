package com.example.template.infra.rest.web

import com.example.template.vocabulary.ApiException
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.InvalidCredentialsException
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.InvalidInputException
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.UnauthenticatedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates domain/application exceptions into RFC 7807 problem responses, so the
 * HTTP status is decided in one place and the use cases stay transport-agnostic. Every
 * response carries a stable `code` member the frontend localizes against.
 */
@RestControllerAdvice
class WebControllerAdvice {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(InvalidInputException::class)
    fun handleInvalidInput(ex: InvalidInputException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex)

    /** Field-anchored validation failure: the offending field travels as a member. */
    @ExceptionHandler(InvalidFieldException::class)
    fun handleInvalidField(ex: InvalidFieldException): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, ex)
        problem.setProperty("field", ex.field)
        return problem
    }

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailAlreadyRegistered(ex: EmailAlreadyRegisteredException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val fieldErrors = ex.bindingResult.fieldErrors
        val message = fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validation failed" }
        val problem = problemDetail(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, message)
        // Bean-validation failures are field-anchored too, same as InvalidFieldException.
        fieldErrors.firstOrNull()?.field?.let { problem.setProperty("field", it) }
        return problem
    }

    private fun problem(status: HttpStatus, ex: ApiException): ProblemDetail =
        problemDetail(status, ex.code, ex.message ?: status.reasonPhrase)
}
