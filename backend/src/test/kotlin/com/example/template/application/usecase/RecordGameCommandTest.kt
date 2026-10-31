package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GameWriteRequest
import com.example.template.application.api.PlayerResultInput
import com.example.template.application.api.RecordGame
import com.example.template.application.api.TeamResultInput
import com.example.template.domain.AdminUserId
import com.example.template.domain.GameKey
import com.example.template.domain.IdGenerator
import com.example.template.domain.PlayerId
import com.example.template.domain.Pairing
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TableId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TichuCall
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.testsupport.InMemoryGameRepository
import com.example.template.testsupport.InMemoryRoundRepository
import com.example.template.testsupport.InMemoryTeamRepository
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.GameNotFoundException
import com.example.template.vocabulary.GameConfirmedException
import com.example.template.vocabulary.GameConcurrentModificationException
import com.example.template.vocabulary.GameValidationException
import com.example.template.vocabulary.InvalidFieldException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Use-case unit tests for the team scoring API (#100), at the inbound-port seam
 * with in-memory doubles. Covers authorization (seat check), round-lock
 * rejection, POST/PUT semantics, optimistic versioning and the team-roster
 * participant rule; engine-internal validation lives in GameScoringTest (#99).
 */
class RecordGameCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val playerA1 = PlayerId("team-a-p1")
    private val playerA2 = PlayerId("team-a-p2")
    private val playerB1 = PlayerId("team-b-p1")
    private val playerB2 = PlayerId("team-b-p2")
    private val seatedPrincipal = AuthenticatedTeam(teamA, tournamentId)

    private fun gameKey() = GameKey(tournamentId, 1, 1, 1)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()

    private val recordGame: RecordGame = RecordGameCommand(
        tournamentRepository,
        roundRepository,
        teamRepository,
        gameRepository,
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

    private fun round(number: Int, locked: Boolean = false) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables = listOf(Table(TableId(1), teamA, teamB))),
        locked = locked,
    )

    private fun validRequest(
        gameNumber: Int = 1,
        version: Int? = null,
        beschiss: Boolean = false,
    ) = GameWriteRequest(
        gameNumber = gameNumber,
        beschiss = beschiss,
        version = version,
        teams = listOf(
            TeamResultInput(
                teamId = teamA,
                baseScore = 55,
                players = listOf(
                    PlayerResultInput(playerA1, 1, TichuCall.SMALL, 0),
                    PlayerResultInput(playerA2, 3, TichuCall.NONE, 0),
                ),
            ),
            TeamResultInput(
                teamId = teamB,
                baseScore = 45,
                players = listOf(
                    PlayerResultInput(playerB1, 2, TichuCall.NONE, 0),
                    PlayerResultInput(playerB2, 4, TichuCall.NONE, 0),
                ),
            ),
        ),
    )

    private fun create(request: GameWriteRequest = validRequest(), principal: AuthenticatedTeam = seatedPrincipal) =
        recordGame.create(tournamentId, 1, 1, request, principal)

    private fun update(request: GameWriteRequest, principal: AuthenticatedTeam = seatedPrincipal) =
        recordGame.update(tournamentId, 1, 1, request, principal)

    // ---- create ---------------------------------------------------------------

    @Test
    fun `creating a game returns version 1 and the derived outcome`() {
        val recorded = create()

        assertEquals(1, recorded.version)
        assertEquals(1, recorded.gameNumber)
        assertEquals(setOf(playerA1), recorded.outcome.tichuSuccesses, "SMALL call finished first")
        assertNull(recorded.outcome.doubleWinTeam)
        assertEquals(155, recorded.outcome.teamPoints[teamA], "base 55 + 100 small bonus")
        assertEquals(45, recorded.outcome.teamPoints[teamB])
        assertEquals(2, recorded.outcome.victoryPoints[teamA])
        assertEquals(0, recorded.outcome.victoryPoints[teamB])
    }

    @Test
    fun `a second POST on the same game slot is rejected as already existing`() {
        create()

        assertFailsWith<com.example.template.vocabulary.GameAlreadyExistsException> { create(validRequest(beschiss = true)) }

        assertEquals(1, gameRepository.find(gameKey())!!.version)
    }

    @Test
    fun `a team not seated at the table is rejected`() {
        val outsider = AuthenticatedTeam(TeamId("team-x"), tournamentId)
        teamRepository.save(team(TeamId("team-x"), "Team X", PlayerId("team-x-p1"), PlayerId("team-x-p2")))

        assertFailsWith<ForbiddenException> { create(principal = outsider) }

        assertNull(gameRepository.find(gameKey()), "nothing was written")
    }

    @Test
    fun `creating a game in a locked round is rejected as confirmed`() {
        roundRepository.save(round(1, locked = true))

        assertFailsWith<GameConfirmedException> { create() }

        assertNull(gameRepository.find(gameKey()))
    }

    @Test
    fun `the submitted teams must be the table's playing teams`() {
        val teamC = TeamId("team-c")
        teamRepository.save(team(teamC, "Team C", PlayerId("team-c-p1"), PlayerId("team-c-p2")))
        val wrongTeams = validRequest().let { request ->
            request.copy(teams = listOf(
                request.teams[0],
                request.teams[1].copy(teamId = teamC),
            ))
        }

        assertFailsWith<GameValidationException> { create(wrongTeams) }

        assertNull(gameRepository.find(gameKey()))
    }

    @Test
    fun `participants must be players of their team`() {
        val intruder = validRequest().let { request ->
            request.copy(teams = listOf(
                request.teams[0].copy(players = listOf(
                    request.teams[0].players[0],
                    request.teams[0].players[1].copy(playerId = playerB1),
                )),
                request.teams[1],
            ))
        }

        assertFailsWith<GameValidationException> { create(intruder) }
    }

    @Test
    fun `game number must be 1 to 4`() {
        assertFailsWith<InvalidFieldException> { create(validRequest(gameNumber = 5)) }
    }

    @Test
    fun `the game's tournament settings govern scoring`() {
        tournamentRepository.save(
            tournamentRepository.findById(tournamentId)!!.copy(
                settings = TournamentSettings(allowGrandTichu = false),
            ),
        )
        val withGrand = validRequest().let { request ->
            request.copy(teams = listOf(
                request.teams[0],
                request.teams[1].copy(players = listOf(
                    request.teams[1].players[0],
                    request.teams[1].players[1].copy(call = TichuCall.GRAND),
                )),
            ))
        }

        assertFailsWith<GameValidationException> { create(withGrand) }
    }

    @Test
    fun `a PUT without a version is rejected`() {
        assertFailsWith<InvalidFieldException> { update(validRequest(version = null)) }
    }

    @Test
    fun `a POST carrying a version is rejected - version is PUT-only`() {
        assertFailsWith<InvalidFieldException> { create(validRequest(version = 1)) }
    }

    // ---- update ---------------------------------------------------------------

    @Test
    fun `editing a game bumps the version and re-derives the outcome`() {
        create()

        val recorded = update(validRequest(version = 1).copy(beschiss = true))

        assertEquals(2, recorded.version)
        assertEquals(true, gameRepository.find(gameKey())!!.beschiss)
    }

    @Test
    fun `a PUT to an uncreated game is not found`() {
        assertFailsWith<GameNotFoundException> { update(validRequest(version = 1)) }
    }

    @Test
    fun `a stale version loses - first commit wins`() {
        create()

        val first = update(validRequest(version = 1))
        assertEquals(2, first.version)

        assertFailsWith<GameConcurrentModificationException> { update(validRequest(version = 1)) }

        assertEquals(2, gameRepository.find(gameKey())!!.version, "stale writer changed nothing")
    }

    @Test
    fun `editing a game in a locked round is rejected as confirmed`() {
        create()
        roundRepository.save(round(1, locked = true))

        assertFailsWith<GameConfirmedException> { update(validRequest(version = 1)) }
    }
}
