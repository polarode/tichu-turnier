package tichu.application.usecase

import tichu.application.api.TeamStatisticsEntry
import tichu.application.api.TournamentStatistics
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
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.ResultsNotPublishedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for the spectator's statistics read (S12, #121): a tournament that is not
 * public or not visible to spectators is not found, a public one only shows its statistics
 * once PUBLISHED.
 */
class GetPublicStatisticsQueryTest {

    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val publicId = TournamentId("t-public")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val query = GetPublicStatisticsQuery(
        tournamentRepository,
        teamRepository,
        PlayedRoundsReader(roundRepository, gameRepository, confirmationRepository),
    )

    private fun team(id: String, name: String) = teamRepository.save(
        Team(
            TeamId(id),
            publicId,
            name,
            listOf(
                Player(PlayerId("$id-1"), publicId, TeamId(id), "$name one"),
                Player(PlayerId("$id-2"), publicId, TeamId(id), "$name two"),
            ),
        ),
    )

    private fun tournament(id: String, status: TournamentStatus, public: Boolean) {
        tournamentRepository.save(
            Tournament(
                TournamentId(id), owner, "Spring Classic", "", 16, TournamentSettings(), status, now, now,
                publicVisibility = public,
            ),
        )
    }

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(publicId)!!.copy(status = status))
    }

    @BeforeEach
    fun setUp() {
        tournament("t-public", TournamentStatus.ACTIVE, public = true)
        tournament("t-private", TournamentStatus.PUBLISHED, public = false)
        tournament("t-setup", TournamentStatus.SETUP, public = true)
        team("a", "Falcons")
        team("b", "Owls")
        roundRepository.save(
            Round(RoundId("r-1"), publicId, 1, Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b")))))),
        )
        // Four games; Falcons' first player calls and makes a grand Tichu every time.
        // Falcons win each game 60 to 40 with finishes 1+3 against 2+4, so 2 VP per game.
        (1..4).forEach { gameNumber ->
            gameRepository.create(
                Game.record(
                    GameKey(publicId, 1, 1, gameNumber),
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
        listOf("a", "b").forEach {
            confirmationRepository.add(TableKey(publicId, 1, 1), TeamId(it), now)
        }
    }

    @Test
    fun `a non-public tournament is not found`() {
        assertFailsWith<NotFoundException> { query.get(TournamentId("t-private")) }
    }

    @Test
    fun `a public tournament that has not started is not found`() {
        assertFailsWith<NotFoundException> { query.get(TournamentId("t-setup")) }
    }

    @Test
    fun `an unknown tournament is not found`() {
        assertFailsWith<NotFoundException> { query.get(TournamentId("t-missing")) }
    }

    @Test
    fun `a public tournament that is not published yet answers results not published`() {
        listOf(TournamentStatus.ACTIVE, TournamentStatus.COMPLETED).forEach { status ->
            setStatus(status)

            val ex = assertFailsWith<ResultsNotPublishedException>("status $status") { query.get(publicId) }
            assertEquals(ErrorCode.RESULTS_NOT_PUBLISHED, ex.code)
        }
    }

    @Test
    fun `once published a public tournament shows the statistics with the hand-worked values`() {
        setStatus(TournamentStatus.PUBLISHED)

        assertEquals(
            TournamentStatistics(
                teams = listOf(
                    TeamStatisticsEntry("Falcons", TeamStatistics(TeamId("a"), 0, 4, 0, 0.0, 0, 4, 0, 0, 130.0)),
                    TeamStatisticsEntry("Owls", TeamStatistics(TeamId("b"), 0, 0, 0, 0.0, 0, 0, 0, 0, null)),
                ),
                highlights = listOf(
                    Highlight(HighlightMetric.SUCCESSFUL_TICHU, 4.0, listOf(TeamId("a"))),
                    Highlight(HighlightMetric.TICHU_CALLS, 4.0, listOf(TeamId("a"))),
                    Highlight(HighlightMetric.GOOD_HORSE, 130.0, listOf(TeamId("a"))),
                ),
                trends = listOf(
                    TeamTrend(TeamId("a"), listOf(TrendPoint(1, 8))),
                    TeamTrend(TeamId("b"), listOf(TrendPoint(1, 0))),
                ),
            ),
            query.get(publicId),
        )
    }
}
