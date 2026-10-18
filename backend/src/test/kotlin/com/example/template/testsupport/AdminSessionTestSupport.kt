package com.example.template.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/** The session cookie name configured for tests (matches `application.yml`). */
const val ADMIN_SESSION_COOKIE = "admin_session"

/**
 * Registers a fresh admin and logs in, returning the session cookie a test can attach
 * to protected requests. Unique emails keep tests independent within one database.
 */
fun MockMvc.registerAndLoginAdmin(
    objectMapper: ObjectMapper,
    password: String = "s3cret-pw",
): Cookie {
    val email = "admin-${java.util.UUID.randomUUID()}@example.com"
    perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
    )

    val result = perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
    ).andReturn()

    val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
        ?: error("login did not set a session cookie")
    val token = setCookie.substringAfter("$ADMIN_SESSION_COOKIE=").substringBefore(';')
    return Cookie(ADMIN_SESSION_COOKIE, token)
}
