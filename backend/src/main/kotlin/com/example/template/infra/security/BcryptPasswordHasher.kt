package com.example.template.infra.security

import com.example.template.domain.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Driven adapter for [PasswordHasher] backed by bcrypt. Each call to [hash] uses a
 * fresh random salt, so the same password yields different hashes; [matches] verifies
 * by re-hashing with the salt embedded in the stored value.
 */
class BcryptPasswordHasher(
    private val encoder: BCryptPasswordEncoder = BCryptPasswordEncoder(),
) : PasswordHasher {

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        encoder.matches(rawPassword, passwordHash)
}
