package com.example.template.infra.persistence

import com.example.template.domain.AdminSession
import com.example.template.domain.AdminUserId
import com.example.template.domain.SessionId
import com.example.template.domain.TeamId
import com.example.template.domain.TeamSession
import com.example.template.testsupport.InMemoryAdminSessionRepository
import com.example.template.testsupport.InMemoryTeamSessionRepository
import com.example.template.testsupport.MutableClock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Pure unit test of the expired-session sweep (#26): expired rows from both
 * session tables are removed, live rows survive, and a re-run is safe.
 */
class ExpiredSessionSweeperTest {

    private val clock = MutableClock(Instant.parse("2026-01-01T12:00:00Z"))
    private val adminSessions = InMemoryAdminSessionRepository()
    private val teamSessions = InMemoryTeamSessionRepository()
    private val sweeper = ExpiredSessionSweeper(adminSessions, teamSessions, clock)

    private fun adminSession(expiresAt: Instant) = AdminSession(
        id = SessionId("admin-${expiresAt}"),
        tokenHash = "hash-admin-${expiresAt}",
        adminUserId = AdminUserId("admin-1"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = expiresAt,
    )

    private fun teamSession(expiresAt: Instant) = TeamSession(
        id = SessionId("team-${expiresAt}"),
        teamId = TeamId("team-1"),
        tokenHash = "hash-team-${expiresAt}",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = expiresAt,
    )

    @Test
    fun `the sweep removes expired rows, keeps live ones and is safe to re-run`() {
        val expiredAdmin = adminSession(clock.now.minusSeconds(1))
        val liveAdmin = adminSession(clock.now.plus(Duration.ofDays(1)))
        val expiredTeam = teamSession(clock.now.minusSeconds(1))
        val liveTeam = teamSession(clock.now.plus(Duration.ofHours(12)))
        listOf(expiredAdmin, liveAdmin).forEach(adminSessions::save)
        listOf(expiredTeam, liveTeam).forEach(teamSessions::save)

        sweeper.sweep()

        assertNull(adminSessions.findByTokenHash(expiredAdmin.tokenHash), "the expired admin session is gone")
        assertNull(teamSessions.findByTokenHash(expiredTeam.tokenHash), "the expired team session is gone")
        assertNotNull(adminSessions.findByTokenHash(liveAdmin.tokenHash), "the live admin session survives")
        assertNotNull(teamSessions.findByTokenHash(liveTeam.tokenHash), "the live team session survives")

        // A re-run touches nothing and throws nothing.
        sweeper.sweep()
        assertNotNull(adminSessions.findByTokenHash(liveAdmin.tokenHash))
        assertNotNull(teamSessions.findByTokenHash(liveTeam.tokenHash))
    }
}