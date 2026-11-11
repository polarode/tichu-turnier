package tichu.application.usecase

import tichu.testsupport.InMemoryAdminUserRepository
import tichu.application.api.AuthenticatedTeam
import tichu.application.api.CorrectGame
import tichu.application.api.GameWriteRequest
import tichu.application.api.PlayerResultInput
import tichu.application.api.RecordGame
import tichu.application.api.TeamResultInput
import tichu.domain.AdminUserId
import tichu.domain.CoOrganizer
import tichu.domain.GameCorrection
import tichu.domain.GameKey
import tichu.domain.Pairing
import tichu.domain.PlayerId
import tichu.domain.Player
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.GameConcurrentModificationException
import tichu.vocabulary.GameNotFoundException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TableNotCompletedException
import tichu.vocabulary.TournamentNotActiveException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Use-case tests for the organizer's game correction (last-resort fix, ADR 0006
 * follow-up), at the inbound-port seam with in-memory doubles. A correction
 * replaces a game's raw facts at a COMPLETED table, records who corrected it and
 * when, and publishes a game recorded event.
 */
class CorrectGameCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val playerA1 = PlayerId("team-a-p1")
    private val playerA2 = PlayerId("team-a-p2")
    private val playerB1 = PlayerId("team-b-p1")
    private val playerB2 = PlayerId("team-b-p2")
    private val seatedPrincipal = AuthenticatedTeam(teamA, tournamentId)

    private val lead = AdminUserId("admin-lead")
    private val coOrganizer = AdminUserId("admin-co")
    private val stranger = AdminUserId("admin-stranger")
    private val correctedAt = Instant.parse("2026-11-10T09:30:00Z")
    private val clock = Clock.fixed(correctedAt, ZoneOffset.UTC)

    private fun gameKey() = GameKey(tournamentId, 1, 1, 1)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()
    private val tournamentEvents = RecordingTournamentEvents()

    private val recordGame: RecordGame = RecordGameCommand(
        tournamentRepository,
        roundRepository,
        teamRepository,
        gameRepository,
        confirmationRepository,
        tournamentEvents,
    )

    private val correctGame: CorrectGame = CorrectGameCommand(
        tournamentRepository,
        TournamentAccess(tournamentRepository, coOrganizerRepository, InMemoryAdminUserRepository()),
        roundRepository,
        teamRepository,
        gameRepository,
        confirmationRepository,
        clock,
        tournamentEvents,
    )

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = lead,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        coOrganizerRepository.add(CoOrganizer(tournamentId, coOrganizer, Instant.parse("2026-01-01T12:00:00Z")))
        teamRepository.save(team(teamA, "Team Alpha", playerA1, playerA2))
        teamRepository.save(team(teamB, "Team Beta", playerB1, playerB2))
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = tournamentId,
                number = 1,
                pairing = Pairing(tables = listOf(Table(TableId(1), listOf(teamA, teamB)))),
            ),
        )
        recordGame.create(tournamentId, 1, 1, validRequest(), seatedPrincipal)
    }

    private fun team(id: TeamId, name: String, p1: PlayerId, p2: PlayerId) = Team(
        id = id,
        tournamentId = tournamentId,
        name = name,
        players = listOf(
            Player(p1, tournamentId, id, "P1 of $name"),
            Player(p2, tournamentId, id, "P2 of $name"),
        ),
    )

    private fun validRequest(version: Int? = null, beschiss: Boolean = false) = GameWriteRequest(
        gameNumber = 1,
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

    /** Both teams confirm: the table is COMPLETED (ADR 0006). */
    private fun completeTable() {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), teamA, Instant.parse("2026-01-01T13:00:00Z"))
        confirmationRepository.add(TableKey(tournamentId, 1, 1), teamB, Instant.parse("2026-01-01T13:01:00Z"))
    }

    private val gameRecorded = TournamentEvent(
        TournamentEventType.GAME_RECORDED,
        tournamentId,
        RoundId("r-1"),
        TableId(1),
    )

    // ---- correction ----------------------------------------------------------------

    @Test
    fun `a lead organizer corrects a game at a completed table - facts replaced, version bumped, correction recorded`() {
        completeTable()
        // Base scores 60 / 40 instead of 55 / 45; beschiss flagged.
        val corrected = validRequest(version = 1, beschiss = true).let { request ->
            request.copy(teams = listOf(request.teams[0].copy(baseScore = 60), request.teams[1].copy(baseScore = 40)))
        }

        val recorded = correctGame.correct(tournamentId, 1, 1, corrected, lead)

        assertEquals(2, recorded.version)
        assertEquals(160, recorded.outcome.teamPoints[teamA], "base 60 + 100 small bonus")
        assertEquals(40, recorded.outcome.teamPoints[teamB])
        val stored = gameRepository.find(gameKey())!!
        assertEquals(true, stored.beschiss)
        assertEquals(GameCorrection(lead, correctedAt), stored.correction)
    }

    @Test
    fun `correcting a game publishes a game recorded event for its round and table`() {
        completeTable()
        tournamentEvents.published.clear()

        correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead)

        assertEquals(listOf(gameRecorded), tournamentEvents.published)
    }

    // ---- tournament status ------------------------------------------------------------

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))
    }

    @Test
    fun `corrections are accepted while the tournament is active or completed and refused in setup or published`() {
        completeTable()

        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.PUBLISHED)) {
            setStatus(status)

            val ex = assertFailsWith<TournamentNotActiveException> {
                correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead)
            }
            assertEquals(ErrorCode.TOURNAMENT_NOT_ACTIVE, ex.code)
            assertEquals(1, gameRepository.find(gameKey())!!.version, "stored game is unchanged")
        }

        setStatus(TournamentStatus.ACTIVE)
        assertEquals(2, correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead).version)
        setStatus(TournamentStatus.COMPLETED)
        assertEquals(3, correctGame.correct(tournamentId, 1, 1, validRequest(version = 2, beschiss = false), lead).version)
    }

    // ---- table status -----------------------------------------------------------------

    @Test
    fun `a game is corrected only at a completed table - a playing or confirming table answers TABLE_NOT_COMPLETED`() {
        val playing = assertFailsWith<TableNotCompletedException> {
            correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead)
        }
        assertEquals(ErrorCode.TABLE_NOT_COMPLETED, playing.code)

        confirmationRepository.add(TableKey(tournamentId, 1, 1), teamA, Instant.parse("2026-01-01T13:00:00Z"))
        val confirming = assertFailsWith<TableNotCompletedException> {
            correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead)
        }
        assertEquals(ErrorCode.TABLE_NOT_COMPLETED, confirming.code)

        val stored = gameRepository.find(gameKey())!!
        assertEquals(1, stored.version, "nothing changed")
        assertNull(stored.correction)
    }

    // ---- addressing and versions ------------------------------------------------------

    @Test
    fun `correcting a game that was never recorded is not found and creates nothing`() {
        completeTable()

        assertFailsWith<GameNotFoundException> {
            correctGame.correct(tournamentId, 1, 1, validRequest(version = 1).copy(gameNumber = 2), lead)
        }

        assertNull(gameRepository.find(GameKey(tournamentId, 1, 1, 2)))
    }

    @Test
    fun `a stale version loses - the first correction wins`() {
        completeTable()
        assertEquals(2, correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), lead).version)

        assertFailsWith<GameConcurrentModificationException> {
            correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = false), lead)
        }

        assertEquals(2, gameRepository.find(gameKey())!!.version, "stale correction changed nothing")
        assertEquals(true, gameRepository.find(gameKey())!!.beschiss)
    }

    // ---- access ---------------------------------------------------------------------

    @Test
    fun `a co-organizer may correct a game, while a stranger gets the same not-found as any organizer-only verb`() {
        completeTable()

        val recorded = correctGame.correct(tournamentId, 1, 1, validRequest(version = 1, beschiss = true), coOrganizer)
        assertEquals(2, recorded.version)

        val ex = assertFailsWith<NotFoundException> {
            correctGame.correct(tournamentId, 1, 1, validRequest(version = 2, beschiss = false), stranger)
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        assertEquals(2, gameRepository.find(gameKey())!!.version, "stranger changed nothing")
    }
}
