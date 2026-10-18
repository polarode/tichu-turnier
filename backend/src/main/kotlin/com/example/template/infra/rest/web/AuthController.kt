package com.example.template.infra.rest.web

import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.LogoutAdmin
import com.example.template.application.api.RefreshSession
import com.example.template.application.api.RegisterAdmin
import com.example.template.vocabulary.UnauthenticatedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for admin authentication. The raw session token is written only into
 * an httpOnly cookie and never appears in a response body. Constructed as a bean by
 * [com.example.template.infra.rest.RestConfiguration].
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val registerAdmin: RegisterAdmin,
    private val loginAdmin: LoginAdmin,
    private val refreshSession: RefreshSession,
    private val logoutAdmin: LogoutAdmin,
    private val sessionCookie: SessionCookieFactory,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody body: RegisterAdminRequestBody): AdminResponse {
        val user = registerAdmin.register(
            RegisterAdmin.RegisterAdminRequest(
                email = body.email,
                password = body.password,
                inviteToken = body.inviteToken,
            ),
        )
        return AdminResponse(id = user.id.value, email = user.email)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody body: LoginRequestBody,
        response: HttpServletResponse,
    ): AdminResponse {
        val issued = loginAdmin.login(
            LoginAdmin.LoginAdminRequest(email = body.email, password = body.password),
        )
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.session(issued.token).toString())
        return AdminResponse(id = issued.adminUserId.value, email = issued.email)
    }

    @PostMapping("/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AdminResponse {
        val token = sessionCookie.read(request)
            ?: throw UnauthenticatedException("Session is invalid or has expired")

        val issued = refreshSession.refresh(token)
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.session(issued.token).toString())
        return AdminResponse(id = issued.adminUserId.value, email = issued.email)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        sessionCookie.read(request)?.let { logoutAdmin.logout(it) }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.expired().toString())
    }
}
