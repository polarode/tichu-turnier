package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TableId
import com.example.template.domain.TeamId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.domain.TournamentRepository
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.insertAdminRow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-port integration test: the [RoundRepository] adapter must run against
 * real PostgreSQL (Testcontainers). Deliberately does NOT use test transactions —
 * each repository call must commit, so the tests prove data actually reaches the
 * database and survives beyond a single repository instance.
 */
@SpringBootTest(classes = [Application::class])
class RoundRepositoryPersistenceTest(
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val owner = AdminUserId("admin-owner")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        insertAdminRow(jdbcTemplate, owner.value, "[EMAIL]")
    }

    private fun tournament(id: String): Tournament = Tournament(
        id = TournamentId(id),
        ownerId = owner,
        name = "Spring Classic",
        description = "",
        maxTeams = 16,
        settings = TournamentSettings(),
        status = TournamentStatus.ACTIVE,
        createdAt = Instant.parse("2026-01-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
    ).let { tournamentRepository.save(it) }

    private fun round(id: String, tournamentId: String, number: Int) = Round(
        id = RoundId(id),
        tournamentId = TournamentId(tournamentId),
        number = number,
        pairing = com.example.template.domain.Pairing(
            tables = listOf(
                Table(TableId(1), TeamId("team-a"), TeamId("team-b")),
                Table(TableId(2), TeamId("team-c"), TeamId("team-d")),
            ),
        ),
    )

    @Test
    fun `saved round is read back with its tables intact`() {
        tournament("t-1")

        roundRepository.save(round("r-1", "t-1", 1))

        val loaded = roundRepository.findByTournamentAndNumber(TournamentId("t-1"), 1)
        assertEquals(round("r-1", "t-1", 1), loaded)
    }

    @Test
    fun `lookup by unknown round number returns null`() {
        tournament("t-1")
        roundRepository.save(round("r-1", "t-1", 1))

        assertNull(roundRepository.findByTournamentAndNumber(TournamentId("t-1"), 2))
        assertNull(roundRepository.findByTournamentAndNumber(TournamentId("t-2"), 1))
    }

    @Test
    fun `rounds of a tournament come back ordered by round number`() {
        tournament("t-1")
        roundRepository.save(round("r-2", "t-1", 2))
        roundRepository.save(round("r-1", "t-1", 1))

        val rounds = roundRepository.findByTournament(TournamentId("t-1"))

        assertEquals(listOf(1, 2), rounds.map { it.number })
    }

    @Test
    fun `re-saving a round replaces its tables instead of duplicating them`() {
        tournament("t-1")
        roundRepository.save(round("r-1", "t-1", 1))

        val reshuffled = round("r-1", "t-1", 1).copy(
            pairing = com.example.template.domain.Pairing(
                tables = listOf(
                    Table(TableId(1), TeamId("team-d"), TeamId("team-c")),
                    Table(TableId(2), TeamId("team-b"), TeamId("team-a")),
                ),
            ),
        )
        roundRepository.save(reshuffled)

        assertEquals(reshuffled, roundRepository.findByTournamentAndNumber(TournamentId("t-1"), 1))
        assertEquals(2, tableRowCount("r-1"), "no stale table rows remain")
    }

    @Test
    fun `deleting the tournament cascades to its rounds and tables`() {
        tournament("t-1")
        roundRepository.save(round("r-1", "t-1", 1))

        tournamentRepository.delete(TournamentId("t-1"))

        assertTrue(roundRepository.findByTournament(TournamentId("t-1")).isEmpty())
        assertEquals(0, tableRowCount("r-1"))
    }

    private fun tableRowCount(roundId: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM round_tables WHERE round_id = ?", Int::class.java, roundId)!!
}
