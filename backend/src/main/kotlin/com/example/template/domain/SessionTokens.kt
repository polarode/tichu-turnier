package com.example.template.domain

/**
 * Outbound port for opaque session tokens. [generate] must return a value with enough
 * entropy to resist guessing; [hash] must be a stable, one-way digest so only the
 * digest is persisted and lookups stay deterministic.
 */
interface SessionTokens {
    fun generate(): String

    fun hash(token: String): String
}
