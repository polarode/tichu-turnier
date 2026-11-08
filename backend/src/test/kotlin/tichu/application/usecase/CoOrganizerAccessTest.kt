package tichu.application.usecase

import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.CoOrganizer
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
import tichu.application.api.TournamentWriteRequest
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.NotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Use-case tests for co-organizer access (#131). The lead organizer `lead` owns
 * tournament `t-a` and has added `co` as its co-organizer. A co-organizer may read,
 * edit and list rounds; only the lead may delete. Access is per tournament: `co`
 * gains nothing on `t-b`, and a stranger sees nothing at all.
 */
class CoOrganizerAccessTest {

    private val tournamentA = TournamentId("t-a")
    private val tournamentB = TournamentId("t-b")
    private val lead = AdminUserId("admin-lead")
    private val co = AdminUserId("admin-co")
    private val stranger = AdminUserId("admin-stranger")
    private val addedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val adminUserRepository = InMemoryAdminUserRepository()
    private val getTournament = GetTournamentQuery(TournamentAccess(tournamentRepository, coOrganizerRepository), adminUserRepository)
    private val updateTournament = UpdateTournamentCommand(
        tournamentRepository,
        TournamentAccess(tournamentRepository, coOrganizerRepository),
        teamRepository,
        clock,
    )
    private val deleteTournament = DeleteTournamentCommand(
        tournamentRepository,
        TournamentAccess(tournamentRepository, coOrganizerRepository),
    )
    private val listRounds = ListRoundsQuery(
        TournamentAccess(tournamentRepository, coOrganizerRepository),
        roundRepository,
        gameRepository,
        confirmationRepository,
    )

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(tournament(tournamentA))
        tournamentRepository.save(tournament(tournamentB))
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = tournamentA,
                number = 1,
                pairing = Pairing(listOf(Table(TableId(1), listOf(TeamId("team-a"), TeamId("team-b"))))),
            ),
        )
        coOrganizerRepository.add(CoOrganizer(tournamentA, co, addedAt))
        adminUserRepository.save(admin(lead, "lead@example.com"))
    }

    @Test
    fun `a co-organizer of a tournament can read, edit and list its rounds but not delete it`() {
        assertEquals(tournamentA, getTournament.get(tournamentA, co).tournament.id, "co-organizer reads the tournament")

        val updated = updateTournament.update(tournamentA, updateRequest("Renamed by co"), co)
        assertEquals("Renamed by co", updated.name, "co-organizer edits the tournament")

        assertEquals(1, listRounds.list(tournamentA, co).size, "co-organizer lists the rounds")

        assertFailsWith<ForbiddenException> { deleteTournament.delete(tournamentA, co) }
        assertNotNull(tournamentRepository.findById(tournamentA), "the tournament survives a co-organizer's delete")
    }

    @Test
    fun `a stranger gets not-found on read and delete`() {
        assertFailsWith<NotFoundException> { getTournament.get(tournamentA, stranger) }
        assertFailsWith<NotFoundException> { deleteTournament.delete(tournamentA, stranger) }
        assertNotNull(tournamentRepository.findById(tournamentA))
    }

    @Test
    fun `being co-organizer of one tournament gives no access to another`() {
        assertFailsWith<NotFoundException> { getTournament.get(tournamentB, co) }
    }

    private fun tournament(id: TournamentId) = Tournament(
        id = id,
        ownerId = lead,
        name = "Spring Classic",
        description = "",
        maxTeams = 16,
        settings = TournamentSettings(),
        status = TournamentStatus.SETUP,
        createdAt = addedAt,
        updatedAt = addedAt,
    )

    private fun updateRequest(name: String) =
        TournamentWriteRequest(name, "", 16, TournamentSettings())

    private fun admin(id: AdminUserId, email: String) = AdminUser(
        id = id,
        email = email,
        passwordHash = "hash",
        status = AdminStatus.ACTIVE,
        role = AdminRole.ADMIN,
        createdAt = addedAt,
    )
}
