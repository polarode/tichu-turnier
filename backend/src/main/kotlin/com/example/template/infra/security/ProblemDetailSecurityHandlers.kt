package com.example.template.infra.security

import com.example.template.infra.rest.web.problemDetail
import com.example.template.vocabulary.ErrorCode
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * The security filter chain answers authorization failures before any controller or
 * `@RestControllerAdvice` sees them, so it writes the same RFC 7807 shape itself:
 * a stable `code` and an English message, never any resource data.
 */
class ProblemDetailAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = writeProblem(
        objectMapper = objectMapper,
        response = response,
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.UNAUTHENTICATED,
        detail = "Authentication required",
    )
}

/** Maps an authenticated-but-not-allowed request to a 403, kept distinct from 401. */
class ProblemDetailAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = writeProblem(
        objectMapper = objectMapper,
        response = response,
        status = HttpStatus.FORBIDDEN,
        code = ErrorCode.FORBIDDEN,
        detail = "Access denied",
    )
}

private fun writeProblem(
    objectMapper: ObjectMapper,
    response: HttpServletResponse,
    status: HttpStatus,
    code: ErrorCode,
    detail: String,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    objectMapper.writeValue(response.outputStream, problemDetail(status, code, detail))
}
