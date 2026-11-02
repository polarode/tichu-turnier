package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GameWriteRequest
import tichu.application.api.PlayerResultInput
import tichu.application.api.TeamResultInput
import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.PlayerId
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TableStatus
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.NotFoundException
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
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val recordGame = RecordGameCommand(
        tournamentRepository, roundRepository, teamRepository, gameRepository, confirmationRepository,
    )
    private val query = GetTeamScoringQuery(
        tournamentRepository, roundRepository, teamRepository, gameRepository, confirmationRepository,
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
            tichu.domain.Player(p1, tournamentId, id, "P1 of $name"),
            tichu.domain.Player(p2, tournamentId, id, "P2 of $name"),
        ),
    )

    private fun round(number: Int, home: TeamId = teamA, away: TeamId = teamB) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables = listOf(Table(TableId(1), listOf(home, away)))),
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
    fun `currentTable reports the confirmation state`() {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), teamB, Instant.parse("2026-01-01T13:00:00Z"))

        val table = query.currentTable(seatedPrincipal)

        assertEquals(TableStatus.CONFIRMING, table.confirmation.status)
        assertEquals(listOf(teamB), table.confirmation.confirmedTeamIds)
    }

    @Test
    fun `currentTable returns the matchup, roster, confirmation state and settings`() {
        val table = query.currentTable(seatedPrincipal)

        assertEquals(tournamentId, table.tournamentId)
        assertEquals(1, table.roundNumber)
        assertEquals(1, table.tableNumber)
        assertEquals(TableStatus.PLAYING, table.confirmation.status)
        assertEquals(emptyList(), table.confirmation.confirmedTeamIds)
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
