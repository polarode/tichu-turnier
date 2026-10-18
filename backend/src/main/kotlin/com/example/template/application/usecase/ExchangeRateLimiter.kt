package com.example.template.application.usecase

import org.springframework.beans.factory.annotation.Value
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, per-source-IP rate limiter for failed team access-link exchanges
 * (#26). After [maxFailures] failures within [window] from one IP, that IP is
 * temporarily blocked.
 *
 * Deliberate properties, per the Phase 5 design:
 * - counting is **per IP, never per token**, so an attacker cannot lock a specific
 *   team out — their failures only ever block their own address;
 * - a blocked caller is indistinguishable from a wrong guess: the exchange use
 *   case rejects with the same generic error and does not even look at the token.
 *
 * Single-instance assumption: the counters live in one JVM's memory (Render free
 * tier is one container). If deployment ever scales out, replace with a shared
 * store behind the same shape — call sites do not change.
 */
class ExchangeRateLimiter(
    private val clock: Clock,
    @param:Value("\${app.auth.exchange-rate-limit.max-failures:5}") private val maxFailures: Int,
    @param:Value("\${app.auth.exchange-rate-limit.window:PT15M}") private val window: Duration,
) {

    private val failures = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    /** Whether this source is currently blocked because of recent failed attempts. */
    fun isBlocked(sourceIp: String, now: Instant = clock.instant()): Boolean =
        recentFailures(sourceIp, now).size >= maxFailures

    fun registerFailure(sourceIp: String, now: Instant = clock.instant()) {
        val timestamps = failures.computeIfAbsent(sourceIp) { ArrayDeque() }
        synchronized(timestamps) {
            timestamps.addLast(now)
            timestamps.removeAll { it.plus(window) < now }
        }
    }

    /** A successful exchange clears the source's history — normal usage never accumulates. */
    fun registerSuccess(sourceIp: String) {
        failures.remove(sourceIp)
    }

    private fun recentFailures(sourceIp: String, now: Instant): List<Instant> {
        val timestamps = failures[sourceIp] ?: return emptyList()
        synchronized(timestamps) {
            timestamps.removeAll { it.plus(window) < now }
            return timestamps.toList()
        }
    }
}