package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.Highlight
import tichu.domain.HighlightMetric
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamResult
import tichu.domain.TeamStatistics
import tichu.domain.TeamTrend
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.domain.TrendPoint
import tichu.application.api.TeamStatisticsEntry
import tichu.application.api.TournamentStatistics
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotStartedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for the admin's statistics read: the tournament's teams, rounds and
 * games fed into the Team Statistics, Highlights and Team Trends (GLOSSARY.md),
 * scoped to the owning admin.
 */
class GetStatisticsQueryTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val query = GetStatisticsQuery(
        tournamentRepository,
        teamRepository,
        PlayedRoundsReader(roundRepository, gameRepository, confirmationRepository),
    )

    private fun team(id: String, name: String) = teamRepository.save(
        Team(
            TeamId(id),
            tournamentId,
            name,
            listOf(
                Player(PlayerId("$id-1"), tournamentId, TeamId(id), "$name one"),
                Player(PlayerId("$id-2"), tournamentId, TeamId(id), "$name two"),
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, TournamentSettings(), TournamentStatus.ACTIVE, now, now),
        )
        team("a", "Falcons")
        team("b", "Owls")
        roundRepository.save(
            Round(RoundId("r-1"), tournamentId, 1, Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b")))))),
        )
        // Four games; Falcons' first player calls and makes a grand Tichu every time.
        // Falcons win each game 60 to 40 with finishes 1+3 against 2+4, so 2 VP per game.
        (1..4).forEach { gameNumber ->
            gameRepository.create(
                Game.record(
                    GameKey(tournamentId, 1, 1, gameNumber),
                    listOf(
                        TeamResult(TeamId("a"), 60, listOf(
                            PlayerResult(PlayerId("a-1"), 1, TichuCall.GRAND, 0),
                            PlayerResult(PlayerId("a-2"), 3, TichuCall.NONE, 0),
                        )),
                        TeamResult(TeamId("b"), 40, listOf(
                            PlayerResult(PlayerId("b-1"), 2, TichuCall.NONE, 0),
                            PlayerResult(PlayerId("b-2"), 4, TichuCall.NONE, 0),
                        )),
                    ),
                    beschiss = false,
                    settings = TournamentSettings(),
                ),
            )
        }
    }

    private fun confirm(vararg teamIds: String) = teamIds.forEach {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId(it), now)
    }

    @Test
    fun `a completed table's statistics carry the team names, highlights and trends`() {
        confirm("a", "b")

        assertEquals(
            TournamentStatistics(
                teams = listOf(
                    TeamStatisticsEntry("Falcons", TeamStatistics(TeamId("a"), 0, 4, 0, 0.0, 0)),
                    TeamStatisticsEntry("Owls", TeamStatistics(TeamId("b"), 0, 0, 0, 0.0, 0)),
                ),
                highlights = listOf(Highlight(HighlightMetric.SUCCESSFUL_TICHU, 4, listOf(TeamId("a")))),
                trends = listOf(
                    TeamTrend(TeamId("a"), listOf(TrendPoint(1, 8))),
                    TeamTrend(TeamId("b"), listOf(TrendPoint(1, 0))),
                ),
            ),
            query.get(tournamentId, owner),
        )
    }

    @Test
    fun `the organizer still reads the statistics after finishing and after publishing`() {
        confirm("a", "b")

        listOf(TournamentStatus.COMPLETED, TournamentStatus.PUBLISHED).forEach { status ->
            tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))

            val falcons = query.get(tournamentId, owner).teams.first()
            assertEquals("Falcons", falcons.teamName, "status $status")
            assertEquals(4, falcons.statistics.successfulGrandTichu, "status $status")
        }
    }

    @Test
    fun `a tournament still in setup has no statistics yet`() {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.SETUP))

        val ex = assertFailsWith<TournamentNotStartedException> { query.get(tournamentId, owner) }
        assertEquals(ErrorCode.TOURNAMENT_NOT_STARTED, ex.code)
    }

    @Test
    fun `a foreign admin cannot read another admin's statistics`() {
        assertFailsWith<NotFoundException> { query.get(tournamentId, AdminUserId("admin-intruder")) }
    }
}
