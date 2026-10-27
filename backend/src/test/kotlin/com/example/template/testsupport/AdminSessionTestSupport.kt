package com.example.template.testsupport

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Instant
import java.util.UUID

/** The session cookie name configured for tests (matches `application.yml`). */
const val ADMIN_SESSION_COOKIE = "admin_session"

/**
 * Seeds an admin directly through the repository. Registration no longer yields a
 * usable account (it creates a PENDING one, Phase 4), so integration tests that need a
 * working login seed an ACTIVE admin explicitly.
 */
fun seedAdmin(
    adminUsers: AdminUserRepository,
    passwordHasher: PasswordHasher,
    email: String,
    password: String = "s3cret-pw",
    status: AdminStatus = AdminStatus.ACTIVE,
    role: AdminRole = AdminRole.ADMIN,
): AdminUser = adminUsers.save(
    AdminUser(
        id = AdminUserId(UUID.randomUUID().toString()),
        email = email,
        passwordHash = passwordHasher.hash(password),
        status = status,
        role = role,
        createdAt = Instant.now(),
    ),
)

/** Logs an existing account in and returns the session cookie it was issued. */
fun MockMvc.loginAndCaptureSession(
    objectMapper: ObjectMapper,
    email: String,
    password: String = "s3cret-pw",
): Cookie {
    val result = perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
    ).andReturn()

    val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
        ?: error("login did not set a session cookie (status ${result.response.status})")
    val token = setCookie.substringAfter("$ADMIN_SESSION_COOKIE=").substringBefore(';')
    return Cookie(ADMIN_SESSION_COOKIE, token)
}

/**
 * Seeds a fresh ACTIVE admin and logs it in, returning the session cookie a test can
 * attach to protected requests. Unique emails keep tests independent within one database.
 */
fun MockMvc.loginAsSeededAdmin(
    adminUsers: AdminUserRepository,
    passwordHasher: PasswordHasher,
    objectMapper: ObjectMapper,
    password: String = "s3cret-pw",
): Cookie {
    val email = "admin-${UUID.randomUUID()}@example.com"
    seedAdmin(adminUsers, passwordHasher, email, password)
    return loginAndCaptureSession(objectMapper, email, password)
}
