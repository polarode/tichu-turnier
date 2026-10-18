package com.example.template.infra.security

import com.example.template.domain.SessionTokens
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Driven adapter for [SessionTokens]. Tokens are 256 bits from [SecureRandom], encoded
 * URL-safe for cookie/header transport; only their SHA-256 digest is persisted. A fast
 * digest is sufficient here — unlike passwords, the token has full entropy, so there is
 * nothing to brute-force.
 */
class SecureSessionTokens(
    private val secureRandom: SecureRandom = SecureRandom(),
) : SessionTokens {

    override fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
