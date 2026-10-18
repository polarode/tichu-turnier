package com.example.template.testsupport

import com.example.template.domain.SessionTokens
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Test double for [SessionTokens]: generates realistic 256-bit tokens and records
 * every raw token it has produced, so tests can assert the raw material never
 * reaches persistence while still knowing what it was. Hashing mirrors the
 * production adapter (SHA-256 hex).
 */
class RecordingSessionTokens : SessionTokens {

    val generated = mutableListOf<String>()
    private val secureRandom = SecureRandom()

    override fun generate(): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        generated.add(token)
        return token
    }

    override fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Generates tokens from a fixed prefix; collisions are impossible within a test run. */
class SequentialSessionTokens : SessionTokens {

    private var counter = 0
    private val rawTokens = mutableListOf<String>()

    override fun generate(): String {
        val token = "raw-token-${++counter}"
        rawTokens.add(token)
        return token
    }

    override fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}