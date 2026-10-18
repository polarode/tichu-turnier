package com.example.template.infra.persistence

import com.example.template.domain.AdminSessionRepository
import com.example.template.domain.TeamSessionRepository
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

/**
 * Hourly, idempotent sweep of expired sessions (#26): bulk-deletes rows whose
 * `expires_at` has passed from `admin_sessions` and `team_sessions`, so the
 * session tables do not grow without bound. The delete statements are bulk JPQL
 * (no entity hydration, no optimistic-lock checks), which makes them safe to run
 * concurrently on every instance; the re-run-after-zero-rows case is harmless.
 *
 * Backed by the `expires_at` indexes created with the session tables. Timing is
 * configurable via `app.auth.sweep-interval-ms`; the default is hourly.
 */
class ExpiredSessionSweeper(
    private val adminSessions: AdminSessionRepository,
    private val teamSessions: TeamSessionRepository,
    private val clock: Clock,
) {

    @Scheduled(fixedDelayString = "\${app.auth.sweep-interval-ms:3600000}")
    fun sweep() {
        val now = clock.instant()
        adminSessions.deleteExpiredBefore(now)
        teamSessions.deleteExpiredBefore(now)
    }
}