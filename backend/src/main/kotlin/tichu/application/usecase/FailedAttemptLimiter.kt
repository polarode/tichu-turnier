package tichu.application.usecase

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, per-source-IP limiter for failed attempts. After [maxFailures] failures
 * within [window] from one IP, that IP is temporarily blocked. Each guarded entry point
 * gets its own instance: team access-link exchanges (#26) and admin logins.
 *
 * Counting is **per IP, never per account or token**, so an attacker cannot lock a
 * specific team or organizer out — their failures only ever block their own address.
 * How a blocked caller is answered is the use case's decision (see
 * [ExchangeTeamAccessCommand] and [LoginAdminCommand]).
 *
 * Single-instance assumption: the counters live in one JVM's memory (the VPS
 * deploy is one container). If deployment ever scales out, replace with a shared
 * store behind the same shape — call sites do not change.
 *
 * A plain class wired via [UseCaseConfiguration] — thresholds are constructor
 * arguments resolved from configuration there, never Spring annotations here.
 */
class FailedAttemptLimiter(
    private val clock: Clock,
    private val maxFailures: Int,
    private val window: Duration,
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