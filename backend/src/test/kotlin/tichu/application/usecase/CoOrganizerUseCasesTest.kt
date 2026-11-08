package tichu.application.usecase

import tichu.application.api.OrganizerRole
import tichu.domain.AdminRole
import tichu.domain.CoOrganizer
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.vocabulary.AlreadyCoOrganizerException
import tichu.vocabulary.CannotAddSelfAsCoOrganizerException
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.NoActiveAdminWithEmailException
import tichu.vocabulary.NotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Use-case tests for managing co-organizers (#131). The lead organizer `lead` owns
 * tournament `t-a` and adds co-organizers by email. Only active admins can be added;
 * unknown, pending and disabled emails are indistinguishable to the caller.
 */
class CoOrganizerUseCasesTest {

    private val tournamentA = TournamentId("t-a")
    private val lead = AdminUserId("admin-lead")
    private val co = AdminUserId("admin-co")
    private val alice = AdminUserId("admin-alice")
    private val bob = AdminUserId("admin-bob")
    private val pending = AdminUserId("admin-pending")
    private val disabled = AdminUserId("admin-disabled")
    private val stranger = AdminUserId("admin-stranger")
    private val createdAt = Instant.parse("2026-01-01T09:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val adminUserRepository = InMemoryAdminUserRepository()

    private fun tournamentAccess() = TournamentAccess(tournamentRepository, coOrganizerRepository)
    private val listOrganizers get() = ListOrganizersQuery(tournamentAccess(), coOrganizerRepository, adminUserRepository)
    private val addCoOrganizer get() = AddCoOrganizerCommand(tournamentAccess(), coOrganizerRepository, adminUserRepository, clock)
    private val getTournament get() = GetTournamentQuery(tournamentAccess(), adminUserRepository)
    private val listTournaments get() = ListTournamentsQuery(tournamentRepository, tournamentAccess(), coOrganizerRepository, adminUserRepository)

    @BeforeEach
    fun setUp() {
        adminUserRepository.save(admin(lead, "lead@example.com", AdminStatus.ACTIVE))
        adminUserRepository.save(admin(co, "co@example.com", AdminStatus.ACTIVE))
        adminUserRepository.save(admin(alice, "alice@example.com", AdminStatus.ACTIVE))
        adminUserRepository.save(admin(bob, "bob@example.com", AdminStatus.ACTIVE))
        adminUserRepository.save(admin(pending, "pending@example.com", AdminStatus.PENDING))
        adminUserRepository.save(admin(disabled, "disabled@example.com", AdminStatus.DISABLED))
        adminUserRepository.save(admin(stranger, "stranger@example.com", AdminStatus.ACTIVE))
        tournamentRepository.save(tournament(tournamentA, TournamentStatus.SETUP))
    }

    @Test
    fun `lead adds an active admin by normalized email and the co-organizer gains access`() {
        val result = addCoOrganizer.add(tournamentA, "  CO@Example.COM ", lead)

        assertEquals(lead, result.lead.adminUserId)
        assertEquals("lead@example.com", result.lead.email)
        assertEquals(listOf(co), result.coOrganizers.map { it.adminUserId })
        assertEquals("co@example.com", result.coOrganizers.single().email)

        val listed = listOrganizers.list(tournamentA, lead)
        assertEquals(listOf(co), listed.coOrganizers.map { it.adminUserId })

        assertEquals(tournamentA, getTournament.get(tournamentA, co).tournament.id, "the new co-organizer passes the organizer check")
    }

    @Test
    fun `adding a co-organizer works in every tournament status`() {
        val statuses = listOf(
            TournamentStatus.SETUP,
            TournamentStatus.ACTIVE,
            TournamentStatus.COMPLETED,
            TournamentStatus.PUBLISHED,
        )
        statuses.forEachIndexed { index, status ->
            val id = TournamentId("t-status-$index")
            tournamentRepository.save(tournament(id, status))

            val result = addCoOrganizer.add(id, "co@example.com", lead)

            assertEquals(listOf(co), result.coOrganizers.map { it.adminUserId }, "added in $status")
        }
    }

    @Test
    fun `unknown, pending and disabled emails are refused with one identical error`() {
        val unknown = assertFailsWith<NoActiveAdminWithEmailException> {
            addCoOrganizer.add(tournamentA, "nobody@example.com", lead)
        }
        val pendingEx = assertFailsWith<NoActiveAdminWithEmailException> {
            addCoOrganizer.add(tournamentA, "pending@example.com", lead)
        }
        val disabledEx = assertFailsWith<NoActiveAdminWithEmailException> {
            addCoOrganizer.add(tournamentA, "disabled@example.com", lead)
        }

        for (ex in listOf(unknown, pendingEx, disabledEx)) {
            assertEquals(ErrorCode.NO_ACTIVE_ADMIN_WITH_EMAIL, ex.code)
            assertEquals("email", ex.field)
        }
        assertEquals(unknown.message, pendingEx.message, "pending must not be distinguishable from unknown")
        assertEquals(unknown.message, disabledEx.message, "disabled must not be distinguishable from unknown")
        assertTrue(listOrganizers.list(tournamentA, lead).coOrganizers.isEmpty(), "nothing was added")
    }

    @Test
    fun `the lead organizer cannot add themself as co-organizer`() {
        val ex = assertFailsWith<CannotAddSelfAsCoOrganizerException> {
            addCoOrganizer.add(tournamentA, "lead@example.com", lead)
        }
        assertEquals(ErrorCode.CANNOT_ADD_SELF_AS_CO_ORGANIZER, ex.code)
        assertEquals("email", ex.field)
    }

    @Test
    fun `adding an admin who already co-organizes the tournament is a conflict`() {
        addCoOrganizer.add(tournamentA, "co@example.com", lead)

        val ex = assertFailsWith<AlreadyCoOrganizerException> {
            addCoOrganizer.add(tournamentA, "co@example.com", lead)
        }
        assertEquals(ErrorCode.ALREADY_CO_ORGANIZER, ex.code)
        assertEquals("email", ex.field)
    }

    @Test
    fun `a co-organizer cannot add co-organizers`() {
        addCoOrganizer.add(tournamentA, "co@example.com", lead)

        assertFailsWith<ForbiddenException> { addCoOrganizer.add(tournamentA, "alice@example.com", co) }
    }

    @Test
    fun `a stranger gets not-found when adding or listing`() {
        assertFailsWith<NotFoundException> { addCoOrganizer.add(tournamentA, "alice@example.com", stranger) }
        assertFailsWith<NotFoundException> { listOrganizers.list(tournamentA, stranger) }
    }

    @Test
    fun `co-organizers are listed oldest first`() {
        // Bob is added first, on a later day; Alice is added second, on an earlier day.
        // Insertion order and addedAt order therefore disagree.
        val later = Clock.fixed(Instant.parse("2026-01-05T08:00:00Z"), ZoneOffset.UTC)
        val earlier = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)
        AddCoOrganizerCommand(tournamentAccess(), coOrganizerRepository, adminUserRepository, later)
            .add(tournamentA, "bob@example.com", lead)
        AddCoOrganizerCommand(tournamentAccess(), coOrganizerRepository, adminUserRepository, earlier)
            .add(tournamentA, "alice@example.com", lead)

        val listed = listOrganizers.list(tournamentA, lead)

        assertEquals(listOf(alice, bob), listed.coOrganizers.map { it.adminUserId })
    }

    @Test
    fun `the lead organizer reads the tournament as lead with their own email`() {
        val view = getTournament.get(tournamentA, lead)

        assertEquals(OrganizerRole.LEAD, view.role)
        assertEquals("lead@example.com", view.leadOrganizerEmail)
    }

    @Test
    fun `a co-organizer reads the tournament as co-organizer with the lead's email`() {
        coOrganizerRepository.add(CoOrganizer(tournamentA, co, createdAt))

        val view = getTournament.get(tournamentA, co)

        assertEquals(OrganizerRole.CO, view.role)
        assertEquals("lead@example.com", view.leadOrganizerEmail)
    }

    @Test
    fun `the dashboard of a co-organizer lists the co-organized tournament beside their own, newest first`() {
        // `co` leads t-own, created later; `co` co-organizes t-a, created earlier.
        tournamentRepository.save(
            tournament(TournamentId("t-own"), TournamentStatus.SETUP).copy(
                ownerId = co,
                createdAt = Instant.parse("2026-01-03T09:00:00Z"),
            ),
        )
        coOrganizerRepository.add(CoOrganizer(tournamentA, co, createdAt))

        val listed = listTournaments.list(co).map { Triple(it.tournament.id, it.role, it.leadOrganizerEmail) }

        assertEquals(
            listOf(
                Triple(TournamentId("t-own"), OrganizerRole.LEAD, "co@example.com"),
                Triple(tournamentA, OrganizerRole.CO, "lead@example.com"),
            ),
            listed,
        )
    }

    @Test
    fun `a stranger's dashboard does not list the co-organized tournament`() {
        coOrganizerRepository.add(CoOrganizer(tournamentA, co, createdAt))

        assertTrue(listTournaments.list(stranger).isEmpty())
    }

    private fun admin(id: AdminUserId, email: String, status: AdminStatus) = AdminUser(
        id = id,
        email = email,
        passwordHash = "hash",
        status = status,
        role = AdminRole.ADMIN,
        createdAt = createdAt,
    )

    private fun tournament(id: TournamentId, status: TournamentStatus) = Tournament(
        id = id,
        ownerId = lead,
        name = "Spring Classic",
        description = "",
        maxTeams = 16,
        settings = TournamentSettings(),
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
