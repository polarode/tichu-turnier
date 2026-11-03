package tichu.application.usecase

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
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotStartedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for the admin's standings read: the tournament's teams, rounds,
 * games and confirmations fed into the Standings (GLOSSARY.md), scored with the
 * tournament's own settings, scoped to the owning admin.
 */
class GetStandingsQueryTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val query = GetStandingsQuery(
        tournamentRepository,
        teamRepository,
        roundRepository,
        gameRepository,
        confirmationRepository,
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
        // A grand Tichu bonus of 100 shows the tournament's own settings are used.
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, TournamentSettings(grandTichuBonus = 100), TournamentStatus.ACTIVE, now, now),
        )
        team("a", "Falcons")
        team("b", "Owls")
        roundRepository.save(
            Round(RoundId("r-1"), tournamentId, 1, Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b")))))),
        )
        // Four games; Falcons call and make a grand Tichu every time: 60 + 100 = 160 vs 40.
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
                    settings = TournamentSettings(grandTichuBonus = 100),
                ),
            )
        }
    }

    private fun confirm(vararg teamIds: String) = teamIds.forEach {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId(it), now)
    }

    @Test
    fun `a completed table's games are summed with the tournament's settings`() {
        confirm("a", "b")

        assertEquals(
            listOf(
                StandingsEntry(1, TeamTotals(TeamId("a"), "Falcons", 4, 8, 640)),
                StandingsEntry(2, TeamTotals(TeamId("b"), "Owls", 4, 0, 160)),
            ),
            query.get(tournamentId, owner).entries,
        )
    }

    @Test
    fun `a table not yet completed contributes nothing`() {
        confirm("a")

        assertEquals(listOf(0, 0), query.get(tournamentId, owner).entries.map { it.team.gamesPlayed })
    }

    @Test
    fun `a tournament still in setup has no standings yet`() {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.SETUP))

        val ex = assertFailsWith<TournamentNotStartedException> { query.get(tournamentId, owner) }
        assertEquals(ErrorCode.TOURNAMENT_NOT_STARTED, ex.code)
    }

    @Test
    fun `a foreign admin cannot read another admin's standings`() {
        assertFailsWith<NotFoundException> { query.get(tournamentId, AdminUserId("admin-intruder")) }
    }
}
