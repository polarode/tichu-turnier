package com.example.template.infra.rest.web

import com.example.template.vocabulary.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * Builds the single RFC 7807 problem shape the API returns everywhere: a status, a
 * detail and the stable machine-readable `code` the frontend localizes against.
 * Shared by the `@RestControllerAdvice` and the security filter chain so both emit
 * exactly the same document.
 */
fun problemDetail(status: HttpStatus, code: ErrorCode, detail: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).apply {
        setProperty("code", code.name)
    }
