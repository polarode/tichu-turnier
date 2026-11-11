package tichu.application.usecase

import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.application.api.TournamentWriteRequest
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryTeamRepository
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
 * Use-case tests for the platform owner's access. The lead organizer `lead`
 * owns tournament `t-a` and no one co-organizes it. The owner `owner` (role OWNER) acts on
 * every tournament exactly like its lead organizer, so it may edit and delete `t-a`. A
 * normal admin `stranger` who neither leads nor co-organizes it still sees nothing.
 */
class OwnerTournamentAccessTest {

    private val tournamentA = TournamentId("t-a")
    private val lead = AdminUserId("admin-lead")
    private val owner = AdminUserId("admin-owner")
    private val disabledOwner = AdminUserId("admin-disabled-owner")
    private val stranger = AdminUserId("admin-stranger")
    private val addedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val adminUserRepository = InMemoryAdminUserRepository()
    private val tournamentAccess = TournamentAccess(tournamentRepository, coOrganizerRepository, adminUserRepository)
    private val updateTournament = UpdateTournamentCommand(tournamentRepository, tournamentAccess, teamRepository, clock)
    private val deleteTournament = DeleteTournamentCommand(tournamentRepository, tournamentAccess)

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(tournament(tournamentA))
        adminUserRepository.save(admin(lead, "lead@example.com", AdminRole.ADMIN, AdminStatus.ACTIVE))
        adminUserRepository.save(admin(owner, "owner@example.com", AdminRole.OWNER, AdminStatus.ACTIVE))
        adminUserRepository.save(admin(disabledOwner, "disabled-owner@example.com", AdminRole.OWNER, AdminStatus.DISABLED))
        adminUserRepository.save(admin(stranger, "stranger@example.com", AdminRole.ADMIN, AdminStatus.ACTIVE))
    }

    @Test
    fun `the owner can edit a tournament another admin leads`() {
        val updated = updateTournament.update(tournamentA, updateRequest("Renamed by owner"), owner)
        assertEquals("Renamed by owner", updated.name, "the owner edits the tournament as its lead")
    }

    @Test
    fun `the owner can delete a tournament another admin leads`() {
        deleteTournament.delete(tournamentA, owner)
        assertEquals(null, tournamentRepository.findById(tournamentA), "the owner deletes the tournament as its lead")
    }

    @Test
    fun `a normal admin who neither leads nor co-organizes gets not-found on edit and delete`() {
        assertFailsWith<NotFoundException> { updateTournament.update(tournamentA, updateRequest("Hijacked"), stranger) }
        assertFailsWith<NotFoundException> { deleteTournament.delete(tournamentA, stranger) }
        assertNotNull(tournamentRepository.findById(tournamentA), "the tournament survives a stranger's delete")
    }

    @Test
    fun `a disabled owner gets no access to a tournament it does not lead`() {
        assertFailsWith<NotFoundException> { updateTournament.update(tournamentA, updateRequest("Hijacked"), disabledOwner) }
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

    private fun admin(id: AdminUserId, email: String, role: AdminRole, status: AdminStatus) = AdminUser(
        id = id,
        email = email,
        passwordHash = "hash",
        status = status,
        role = role,
        createdAt = addedAt,
    )
}
