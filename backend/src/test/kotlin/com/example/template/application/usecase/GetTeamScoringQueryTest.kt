package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GameWriteRequest
import com.example.template.application.api.PlayerResultInput
import com.example.template.application.api.TeamResultInput
import com.example.template.domain.AdminUserId
import com.example.template.domain.Pairing
import com.example.template.domain.PlayerId
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.Table
import com.example.template.domain.TableId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TichuCall
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.testsupport.InMemoryGameRepository
import com.example.template.testsupport.InMemoryRoundRepository
import com.example.template.testsupport.InMemoryTeamRepository
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.NotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Use-case unit tests for the team-session scoring reads (S5c, #101) at the
 * inbound-port seam with in-memory doubles. Covers current-round selection, the
 * seat-scoped guards, and that the games list derives the same outcome the write
 * path produced (ADR 0004). The games are seeded through [RecordGameCommand], so
 * the read is exercised against genuinely stored facts.
 */
class GetTeamScoringQueryTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val playerA1 = PlayerId("team-a-p1")
    private val playerA2 = PlayerId("team-a-p2")
    private val playerB1 = PlayerId("team-b-p1")
    private val playerB2 = PlayerId("team-b-p2")
    private val seatedPrincipal = AuthenticatedTeam(teamA, tournamentId)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()

    private val recordGame = RecordGameCommand(
        tournamentRepository, roundRepository, teamRepository, gameRepository,
    )
    private val query = GetTeamScoringQuery(
        tournamentRepository, roundRepository, teamRepository, gameRepository,
    )

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = AdminUserId("admin-owner"),
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        teamRepository.save(team(teamA, "Team Alpha", playerA1, playerA2))
        teamRepository.save(team(teamB, "Team Beta", playerB1, playerB2))
        roundRepository.save(round(1))
    }

    private fun team(id: TeamId, name: String, p1: PlayerId, p2: PlayerId) = Team(
        id = id,
        tournamentId = tournamentId,
        name = name,
        players = listOf(
            com.example.template.domain.Player(p1, tournamentId, id, "P1 of $name"),
            com.example.template.domain.Player(p2, tournamentId, id, "P2 of $name"),
        ),
    )

    private fun round(number: Int, locked: Boolean = false, home: TeamId = teamA, away: TeamId = teamB) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables = listOf(Table(TableId(1), listOf(home, away)))),
        locked = locked,
    )

    private fun seedGame(gameNumber: Int) {
        recordGame.create(
            tournamentId, 1, 1,
            GameWriteRequest(
                gameNumber = gameNumber,
                beschiss = false,
                version = null,
                teams = listOf(
                    TeamResultInput(teamA, 55, listOf(
                        PlayerResultInput(playerA1, 1, TichuCall.SMALL, 0),
                        PlayerResultInput(playerA2, 3, TichuCall.NONE, 0),
                    )),
                    TeamResultInput(teamB, 45, listOf(
                        PlayerResultInput(playerB1, 2, TichuCall.NONE, 0),
                        PlayerResultInput(playerB2, 4, TichuCall.NONE, 0),
                    )),
                ),
            ),
            seatedPrincipal,
        )
    }

    // ---- currentTable -----------------------------------------------------------

    @Test
    fun `currentTable returns the matchup, roster, lock and settings`() {
        val table = query.currentTable(seatedPrincipal)

        assertEquals(tournamentId, table.tournamentId)
        assertEquals(1, table.roundNumber)
        assertEquals(1, table.tableNumber)
        assertEquals(false, table.locked)
        assertEquals(true, table.allowGrandTichu)
        assertEquals(listOf(teamA, teamB), table.teams.map { it.teamId })
        assertEquals("Team Alpha", table.teams[0].teamName)
        assertEquals(listOf(playerA1, playerA2), table.teams[0].players.map { it.playerId })
        assertEquals("P1 of Team Alpha", table.teams[0].players[0].name)
    }

    @Test
    fun `currentTable picks the highest-numbered round seating the team`() {
        roundRepository.save(round(2, home = teamB, away = teamA))

        assertEquals(2, query.currentTable(seatedPrincipal).roundNumber)
    }

    @Test
    fun `currentTable is not-found when the team is not seated anywhere`() {
        teamRepository.save(team(TeamId("team-x"), "Team X", PlayerId("x1"), PlayerId("x2")))

        assertFailsWith<NotFoundException> {
            query.currentTable(AuthenticatedTeam(TeamId("team-x"), tournamentId))
        }
    }

    // ---- games ------------------------------------------------------------------

    @Test
    fun `games returns entered games, ordered, with the derived outcome`() {
        seedGame(2)
        seedGame(1)

        val games = query.games(seatedPrincipal, tournamentId, 1, 1)

        assertEquals(listOf(1, 2), games.map { it.gameNumber })
        val first = games.first()
        assertEquals(1, first.version)
        assertEquals(155, first.outcome.teamPoints[teamA], "base 55 + 100 small bonus")
        assertEquals(2, first.outcome.victoryPoints[teamA])
        assertTrue(first.teams.flatMap { it.players }.any { it.playerId == playerA1 && it.tichuSuccess })
    }

    @Test
    fun `games is empty when none are entered`() {
        assertEquals(emptyList(), query.games(seatedPrincipal, tournamentId, 1, 1))
    }

    @Test
    fun `games rejects a team not seated at the table`() {
        teamRepository.save(team(TeamId("team-x"), "Team X", PlayerId("x1"), PlayerId("x2")))

        assertFailsWith<ForbiddenException> {
            query.games(AuthenticatedTeam(TeamId("team-x"), tournamentId), tournamentId, 1, 1)
        }
    }

    @Test
    fun `games is not-found for a foreign tournament in the path`() {
        assertFailsWith<NotFoundException> {
            query.games(seatedPrincipal, TournamentId("other"), 1, 1)
        }
    }

    @Test
    fun `games is not-found for an unknown round or table`() {
        assertFailsWith<NotFoundException> { query.games(seatedPrincipal, tournamentId, 9, 1) }
        assertFailsWith<NotFoundException> { query.games(seatedPrincipal, tournamentId, 1, 9) }
    }
}
