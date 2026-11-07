package tichu.application.usecase

import tichu.application.api.PublicTournament
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.teamOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Use-case tests for the public home list: only public, non-setup tournaments,
 * grouped by status (ACTIVE, COMPLETED, PUBLISHED), each group sorted by name, with
 * the registered-team count and no owner information.
 */
class ListPublicTournamentsQueryTest {

    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()

    private val query = ListPublicTournamentsQuery(tournamentRepository, teamRepository)

    @BeforeEach
    fun seed() {
        tournament("t-published", "Winter Cup", TournamentStatus.PUBLISHED, public = true)
        tournament("t-setup", "Draft Cup", TournamentStatus.SETUP, public = true)
        tournament("t-private", "Secret Cup", TournamentStatus.ACTIVE, public = false)
        tournament("t-completed", "Autumn Cup", TournamentStatus.COMPLETED, public = true)
        tournament("t-active", "Spring Cup", TournamentStatus.ACTIVE, public = true)

        seedTeam("t-active", "a1")
        seedTeam("t-active", "a2")
        seedTeam("t-active", "a3")
        seedTeam("t-completed", "c1")
        seedTeam("t-published", "p1")
        seedTeam("t-published", "p2")
        seedTeam("t-private", "x1")
    }

    @Test
    fun `lists only public tournaments that are active, completed or published`() {
        val ids = query.list().map { it.id }

        assertEquals(
            listOf(TournamentId("t-active"), TournamentId("t-completed"), TournamentId("t-published")),
            ids,
        )
    }

    @Test
    fun `groups by status in active, completed, published order`() {
        tournament("t-active-b", "Another Active", TournamentStatus.ACTIVE, public = true)

        val statuses = query.list().map { it.status }

        assertEquals(
            listOf(
                TournamentStatus.ACTIVE,
                TournamentStatus.ACTIVE,
                TournamentStatus.COMPLETED,
                TournamentStatus.PUBLISHED,
            ),
            statuses,
        )
    }

    @Test
    fun `sorts names A to Z within a status group, case-insensitively`() {
        tournament("t-z", "zeta open", TournamentStatus.ACTIVE, public = true)
        tournament("t-b", "bravo open", TournamentStatus.ACTIVE, public = true)
        tournament("t-capital", "Charlie open", TournamentStatus.ACTIVE, public = true)

        val activeNames = query.list()
            .filter { it.status == TournamentStatus.ACTIVE }
            .map { it.name }

        assertEquals(listOf("bravo open", "Charlie open", "Spring Cup", "zeta open"), activeNames)
    }

    @Test
    fun `reports the registered team count per tournament`() {
        val byId = query.list().associateBy { it.id }

        assertEquals(3, byId.getValue(TournamentId("t-active")).teamCount)
        assertEquals(1, byId.getValue(TournamentId("t-completed")).teamCount)
        assertEquals(2, byId.getValue(TournamentId("t-published")).teamCount)
    }

    @Test
    fun `a public tournament without teams reports zero teams`() {
        tournament("t-empty", "Empty Cup", TournamentStatus.ACTIVE, public = true)

        val empty = query.list().single { it.id == TournamentId("t-empty") }

        assertEquals(0, empty.teamCount)
    }

    @Test
    fun `exposes the public read model with the tournament name`() {
        val spring = query.list().single { it.id == TournamentId("t-active") }

        assertEquals(PublicTournament(TournamentId("t-active"), "Spring Cup", TournamentStatus.ACTIVE, 3), spring)
    }

    private fun tournament(id: String, name: String, status: TournamentStatus, public: Boolean) {
        tournamentRepository.save(
            Tournament(
                TournamentId(id), owner, name, "", 16, TournamentSettings(), status, now, now,
                publicVisibility = public,
            ),
        )
    }

    private fun seedTeam(tournamentId: String, teamId: String) {
        teamRepository.save(teamOf(TournamentId(tournamentId), teamId))
    }
}
