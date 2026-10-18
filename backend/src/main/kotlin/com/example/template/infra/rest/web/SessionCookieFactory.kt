package com.example.template.infra.rest.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * Builds and reads the admin session cookie. All session cookies are httpOnly and
 * SameSite=Lax so script cannot read them and cross-site requests cannot ride them;
 * `Secure` is configurable for plain-http non-local development.
 */
class SessionCookieFactory(
    private val cookieName: String,
    private val secure: Boolean,
    private val ttl: Duration,
) {

    fun session(token: String): ResponseCookie = base(token).maxAge(ttl).build()

    /** A same-named cookie with zero max-age, instructing the browser to drop it. */
    fun expired(): ResponseCookie = base("").maxAge(Duration.ZERO).build()

    /** Returns the raw session token from the request, or null if the cookie is absent. */
    fun read(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == cookieName }?.value

    private fun base(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(cookieName, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
}
