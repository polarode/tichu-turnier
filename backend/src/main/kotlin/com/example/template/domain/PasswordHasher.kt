package com.example.template.domain

/**
 * Outbound port for password hashing. The application layer reasons about
 * `hash`/`matches` only; the algorithm (bcrypt, Argon2, …) is an infra decision.
 */
interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, passwordHash: String): Boolean
}
