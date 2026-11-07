package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.domain.AdminUserId
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.StandingsEntry
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamResult
import tichu.domain.TeamTotals
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
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.ResultsNotPublishedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for the team's read of the Final Ranking (S7c, #116; GLOSSARY.md):
 * the team sees the ranking only once the organizer has published the results,
 * and then every team's line, scored and ranked exactly like the admin's standings.
 */
class GetFinalRankingQueryTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val principal = AuthenticatedTeam(TeamId("a"), tournamentId)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val query = GetFinalRankingQuery(
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

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))
    }

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, TournamentSettings(), TournamentStatus.ACTIVE, now, now),
        )
        team("a", "Falcons")
        team("b", "Owls")
        team("c", "Hawks")
        team("d", "Ravens")
        // Table 1: Falcons beat Owls 60:40. Table 2: Ravens beat Hawks 60:40.
        roundRepository.save(
            Round(
                RoundId("r-1"),
                tournamentId,
                1,
                Pairing(
                    listOf(
                        Table(TableId(1), listOf(TeamId("a"), TeamId("b"))),
                        Table(TableId(2), listOf(TeamId("d"), TeamId("c"))),
                    ),
                ),
            ),
        )
        gameRepository.create(
            Game.record(
                GameKey(tournamentId, 1, 1, 1),
                listOf(
                    TeamResult(TeamId("a"), 60, listOf(
                        PlayerResult(PlayerId("a-1"), 1, TichuCall.NONE, 0),
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
        gameRepository.create(
            Game.record(
                GameKey(tournamentId, 1, 2, 1),
                listOf(
                    TeamResult(TeamId("d"), 60, listOf(
                        PlayerResult(PlayerId("d-1"), 1, TichuCall.NONE, 0),
                        PlayerResult(PlayerId("d-2"), 3, TichuCall.NONE, 0),
                    )),
                    TeamResult(TeamId("c"), 40, listOf(
                        PlayerResult(PlayerId("c-1"), 2, TichuCall.NONE, 0),
                        PlayerResult(PlayerId("c-2"), 4, TichuCall.NONE, 0),
                    )),
                ),
                beschiss = false,
                settings = TournamentSettings(),
            ),
        )
    }

    @Test
    fun `a team is refused the Final Ranking before the results are published`() {
        listOf(TournamentStatus.SETUP, TournamentStatus.ACTIVE, TournamentStatus.COMPLETED).forEach { status ->
            setStatus(status)

            val ex = assertFailsWith<ResultsNotPublishedException>("status $status") { query.get(principal) }
            assertEquals(ErrorCode.RESULTS_NOT_PUBLISHED, ex.code)
        }
    }

    @Test
    fun `once published a team receives every team's ranking, exact ties sharing a rank`() {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId("a"), now)
        confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId("b"), now)
        confirmationRepository.add(TableKey(tournamentId, 1, 2), TeamId("c"), now)
        confirmationRepository.add(TableKey(tournamentId, 1, 2), TeamId("d"), now)
        setStatus(TournamentStatus.PUBLISHED)

        // Falcons and Ravens both 2 VP / 60 TP and share rank 1; Hawks and Owls
        // both 0 VP / 40 TP and share rank 3 (the next rank skips 2).
        assertEquals(
            listOf(
                StandingsEntry(1, TeamTotals(TeamId("a"), "Falcons", 1, 2, 60)),
                StandingsEntry(1, TeamTotals(TeamId("d"), "Ravens", 1, 2, 60)),
                StandingsEntry(3, TeamTotals(TeamId("c"), "Hawks", 1, 0, 40)),
                StandingsEntry(3, TeamTotals(TeamId("b"), "Owls", 1, 0, 40)),
            ),
            query.get(principal).entries,
        )
    }
}
