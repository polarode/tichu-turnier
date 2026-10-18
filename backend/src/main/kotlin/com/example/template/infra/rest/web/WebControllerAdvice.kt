package com.example.template.infra.rest.web

import com.example.template.vocabulary.ApiException
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.InvalidCredentialsException
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
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validation failed" }
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, message)
    }

    private fun problem(status: HttpStatus, ex: ApiException): ProblemDetail =
        problem(status, ex.code, ex.message ?: status.reasonPhrase)

    private fun problem(status: HttpStatus, code: ErrorCode, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty("code", code.name)
        }
}
