package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TeamId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.teamOf
import tichu.vocabulary.NotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for the public tournament page (S9b, #126): the pairings of every
 * drawn round, tables in table order, teams in seat order with their players, and
 * nothing for tournaments that are not public or not yet started.
 */
class GetPublicTournamentQueryTest {

    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val publicId = TournamentId("t-public")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()

    private val query = GetPublicTournamentQuery(tournamentRepository, teamRepository, roundRepository)

    @BeforeEach
    fun seed() {
        tournament("t-public", "Spring Cup", TournamentStatus.ACTIVE, public = true)
        tournament("t-private", "Secret Cup", TournamentStatus.ACTIVE, public = false)
        tournament("t-setup", "Draft Cup", TournamentStatus.SETUP, public = true)

        // Five teams: an odd count, so the last table of each round is a trio.
        listOf("alpha", "bravo", "charlie", "delta", "echo").forEach {
            teamRepository.save(teamOf(publicId, it, name = it.replaceFirstChar { c -> c.uppercase() }))
        }
        teamRepository.save(teamOf(TournamentId("t-private"), "x1"))
        teamRepository.save(teamOf(TournamentId("t-setup"), "s1"))

        // Round 2 is saved first to show that rounds are ordered by number, not insertion.
        roundRepository.save(
            Round(
                id = RoundId("r-2"),
                tournamentId = publicId,
                number = 2,
                pairing = Pairing(
                    listOf(
                        Table(TableId(1), listOf(TeamId("delta"), TeamId("bravo"))),
                        Table(TableId(2), listOf(TeamId("echo"), TeamId("alpha"), TeamId("charlie"))),
                    ),
                ),
            ),
        )
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = publicId,
                number = 1,
                pairing = Pairing(
                    listOf(
                        Table(TableId(1), listOf(TeamId("alpha"), TeamId("bravo"))),
                        Table(TableId(2), listOf(TeamId("charlie"), TeamId("delta"), TeamId("echo"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `returns every round in number order with its tables, teams in seat order and players`() {
        val detail = query.get(publicId)

        assertEquals(publicId, detail.id)
        assertEquals("Spring Cup", detail.name)
        assertEquals(TournamentStatus.ACTIVE, detail.status)
        assertEquals(listOf(1, 2), detail.rounds.map { it.number })

        val round1 = detail.rounds[0].tables
        assertEquals(listOf(1, 2), round1.map { it.tableNumber })
        assertEquals(listOf(false, true), round1.map { it.trio })
        assertEquals(
            listOf("alpha", "bravo"),
            round1[0].teams.map { it.id.value },
        )
        assertEquals(
            listOf("Alpha", "Bravo"),
            round1[0].teams.map { it.name },
        )
        assertEquals(
            listOf(listOf("Alpha one", "Alpha two"), listOf("Bravo one", "Bravo two")),
            round1[0].teams.map { team -> team.players.map { it.name } },
        )
        assertEquals(
            listOf("charlie", "delta", "echo"),
            round1[1].teams.map { it.id.value },
        )
    }

    @Test
    fun `the trio table of a later round is flagged and keeps its seat order`() {
        val round2 = query.get(publicId).rounds[1].tables

        assertEquals(listOf(false, true), round2.map { it.trio })
        assertEquals(listOf("delta", "bravo"), round2[0].teams.map { it.id.value })
        assertEquals(listOf("echo", "alpha", "charlie"), round2[1].teams.map { it.id.value })
        assertEquals(
            listOf("Echo one", "Echo two"),
            round2[1].teams[0].players.map { it.name },
        )
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

    private fun tournament(id: String, name: String, status: TournamentStatus, public: Boolean) {
        tournamentRepository.save(
            Tournament(
                TournamentId(id), owner, name, "", 16, TournamentSettings(), status, now, now,
                publicVisibility = public,
            ),
        )
    }
}
