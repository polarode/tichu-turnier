package tichu.application.usecase

import tichu.application.api.TeamWriteRequest
import tichu.application.api.TournamentWriteRequest
import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.InMemoryTeamTokenRepository
import tichu.testsupport.RecordingSessionTokens
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.InvalidFieldException
import tichu.vocabulary.MaxTeamsBelowTeamCountException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotEditableException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit test: no Spring context. The ports are satisfied with a real in-memory
 * repository and a tiny fake for the id generator, so the use cases are exercised
 * in isolation and deterministically.
 *
 * The ownership rule is inherited from the admin-auth phase and proven here, at the
 * use-case seam: two admins (`owner` and `intruder`) exercise every verb, and the
 * intruder sees nothing.
 */
class TournamentUseCasesTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val laterClock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)
    private val repository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "tournament-${++counter}"
    }

    private val owner = AdminUserId("admin-owner")
    private val intruder = AdminUserId("admin-intruder")

    private val createTournament = CreateTournamentCommand(repository, idGenerator, fixedClock)
    private val adminUserRepository = InMemoryAdminUserRepository().apply {
        save(admin(AdminUserId("admin-owner"), "owner@example.com"))
        save(admin(AdminUserId("admin-intruder"), "intruder@example.com"))
    }
    private val listTournaments = ListTournamentsQuery(repository, InMemoryCoOrganizerRepository(), adminUserRepository)
    private val getTournament = GetTournamentQuery(TournamentAccess(repository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), adminUserRepository)
    private val updateTournament = UpdateTournamentCommand(repository, TournamentAccess(repository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teamRepository, laterClock)
    private val deleteTournament = DeleteTournamentCommand(repository, TournamentAccess(repository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()))
    private val teamTokenRepository = InMemoryTeamTokenRepository()
    private val sessionTokens = RecordingSessionTokens()
    private val createTeam = CreateTeamCommand(TournamentAccess(repository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teamRepository, teamTokenRepository, sessionTokens, idGenerator, fixedClock)

    private fun create(name: String = "Spring Classic", asAdmin: AdminUserId = owner) =
        createTournament.create(
            TournamentWriteRequest(name, "", 16),
            asAdmin,
        )

    private fun updateRequest(
        name: String = "Spring Classic",
        description: String = "",
        maxTeams: Int = 16,
        settings: TournamentSettings = TournamentSettings(),
    ) = TournamentWriteRequest(name, description, maxTeams, settings)

    private fun addTeam(
        tournamentId: TournamentId,
        name: String = "Team Alpha",
        playerOne: String = "Alice",
        playerTwo: String = "Bob",
    ) = createTeam.create(tournamentId, TeamWriteRequest(name, playerOne, playerTwo), owner)

    @Test
    fun `creates a tournament in setup status with generated id and the caller as owner`() {
        val tournament = createTournament.create(
            TournamentWriteRequest(
                name = "  Spring Classic  ",
                description = "  annual invitational  ",
                maxTeams = 16,
                settings = TournamentSettings(grandTichuBonus = 100),
            ),
            owner,
        )

        assertEquals(TournamentId("tournament-1"), tournament.id)
        assertEquals(owner, tournament.ownerId, "the creating admin is recorded as owner")
        assertEquals("Spring Classic", tournament.name, "name is trimmed")
        assertEquals("annual invitational", tournament.description, "description is trimmed")
        assertEquals(16, tournament.maxTeams)
        assertEquals(TournamentSettings(grandTichuBonus = 100), tournament.settings)
        assertEquals(TournamentStatus.SETUP, tournament.status, "new tournaments start in setup")
        assertEquals(fixedClock.instant(), tournament.createdAt)
        assertEquals(fixedClock.instant(), tournament.updatedAt)
    }

    @Test
    fun `defaults the grand tichu bonus to 200`() {
        val tournament = create()

        assertEquals(TournamentSettings(grandTichuBonus = 200), tournament.settings)
    }

    @Test
    fun `defaults the total rounds to 4`() {
        assertEquals(4, create().settings.totalRounds)
    }

    @Test
    fun `rejects total rounds outside 1 to 20 anchored to the totalRounds field`() {
        for (totalRounds in listOf(0, 21)) {
            val ex = assertFailsWith<InvalidFieldException> {
                createTournament.create(
                    TournamentWriteRequest("Classic", "", 16, TournamentSettings(totalRounds = totalRounds)),
                    owner,
                )
            }
            assertEquals("totalRounds", ex.field)
        }
        for (totalRounds in listOf(1, 20)) {
            val tournament = createTournament.create(
                TournamentWriteRequest("Classic", "", 16, TournamentSettings(totalRounds = totalRounds)),
                owner,
            )
            assertEquals(totalRounds, tournament.settings.totalRounds)
        }
    }

    @Test
    fun `rejects a blank name anchored to the name field`() {
        val ex = assertFailsWith<InvalidFieldException> {
            createTournament.create(
                TournamentWriteRequest("   ", "", 16),
                owner,
            )
        }
        assertEquals("name", ex.field)
    }

    @Test
    fun `rejects a name longer than 255 characters anchored to the name field`() {
        val ex = assertFailsWith<InvalidFieldException> {
            createTournament.create(
                TournamentWriteRequest("x".repeat(256), "", 16),
                owner,
            )
        }
        assertEquals("name", ex.field)
    }

    @Test
    fun `rejects a non-positive max team count anchored to the maxTeams field`() {
        for (maxTeams in listOf(0, -3)) {
            val ex = assertFailsWith<InvalidFieldException> {
                createTournament.create(
                    TournamentWriteRequest("Classic", "", maxTeams),
                    owner,
                )
            }
            assertEquals("maxTeams", ex.field)
        }
    }

    @Test
    fun `rejects a grand tichu bonus other than 100 or 200 anchored to the grandTichuBonus field`() {
        for (bonus in listOf(0, 150, 300)) {
            val ex = assertFailsWith<InvalidFieldException> {
                createTournament.create(
                    TournamentWriteRequest("Classic", "", 16, TournamentSettings(grandTichuBonus = bonus)),
                    owner,
                )
            }
            assertEquals("grandTichuBonus", ex.field)
        }
    }

    @Test
    fun `rejects a double win bonus other than 100 or 200 anchored to the doubleWinBonus field`() {
        for (bonus in listOf(0, 150, 300)) {
            val ex = assertFailsWith<InvalidFieldException> {
                createTournament.create(
                    TournamentWriteRequest("Classic", "", 16, TournamentSettings(doubleWinBonus = bonus)),
                    owner,
                )
            }
            assertEquals("doubleWinBonus", ex.field)
        }
    }

    @Test
    fun `owner reads their own tournament`() {
        val created = create()

        assertEquals(created, getTournament.get(created.id, owner).tournament)
    }

    @Test
    fun `listing returns only the caller's own tournaments`() {
        val mine = create("mine")
        val alsoMine = create("also mine")
        val theirs = create("theirs", asAdmin = intruder)

        val ownerIds = listTournaments.list(owner).map { it.tournament.id }.toSet()
        val intruderIds = listTournaments.list(intruder).map { it.tournament.id }.toSet()

        assertEquals(setOf(mine.id, alsoMine.id), ownerIds)
        assertEquals(setOf(theirs.id), intruderIds)
    }

    @Test
    fun `a foreign admin cannot read another admin's tournament`() {
        val created = create()

        assertFailsWith<NotFoundException> { getTournament.get(created.id, intruder) }
    }

    @Test
    fun `getting a missing tournament fails`() {
        assertFailsWith<NotFoundException> { getTournament.get(TournamentId("nope"), owner) }
    }

    @Test
    fun `update replaces the editable fields, keeps identity, and bumps updatedAt`() {
        val created = create()

        val updated = updateTournament.update(
            created.id,
            updateRequest(
                name = "  Renamed Classic  ",
                description = "  new description  ",
                maxTeams = 8,
                settings = TournamentSettings(grandTichuBonus = 100),
            ),
            owner,
        )

        assertEquals(created.id, updated.id, "the id is stable")
        assertEquals(owner, updated.ownerId, "the owner is stable")
        assertEquals("Renamed Classic", updated.name, "name is trimmed")
        assertEquals("new description", updated.description, "description is trimmed")
        assertEquals(8, updated.maxTeams)
        assertEquals(TournamentSettings(grandTichuBonus = 100), updated.settings)
        assertEquals(TournamentStatus.SETUP, updated.status, "update does not change status")
        assertEquals(created.createdAt, updated.createdAt, "createdAt is preserved")
        assertEquals(laterClock.instant(), updated.updatedAt, "updatedAt is bumped")
        assertEquals(updated, getTournament.get(created.id, owner).tournament, "the update is persisted")
    }

    @Test
    fun `update rejects a blank name anchored to the name field`() {
        val created = create()

        val ex = assertFailsWith<InvalidFieldException> {
            updateTournament.update(created.id, updateRequest(name = "   "), owner)
        }

        assertEquals("name", ex.field)
        assertEquals(created, repository.findById(created.id), "a rejected update changes nothing")
    }

    @Test
    fun `update rejects a non-positive max team count anchored to the maxTeams field`() {
        val created = create()

        for (maxTeams in listOf(0, -3)) {
            val ex = assertFailsWith<InvalidFieldException> {
                updateTournament.update(created.id, updateRequest(maxTeams = maxTeams), owner)
            }
            assertEquals("maxTeams", ex.field)
        }
    }

    @Test
    fun `update rejects a grand tichu bonus other than 100 or 200 anchored to the grandTichuBonus field`() {
        val created = create()

        val ex = assertFailsWith<InvalidFieldException> {
            updateTournament.update(created.id, updateRequest(settings = TournamentSettings(grandTichuBonus = 150)), owner)
        }
        assertEquals("grandTichuBonus", ex.field)
    }

    @Test
    fun `update rejects a double win bonus other than 100 or 200 anchored to the doubleWinBonus field`() {
        val created = create()

        val ex = assertFailsWith<InvalidFieldException> {
            updateTournament.update(created.id, updateRequest(settings = TournamentSettings(doubleWinBonus = 150)), owner)
        }
        assertEquals("doubleWinBonus", ex.field)
    }

    @Test
    fun `update is rejected once the tournament has started`() {
        val created = create()
        repository.save(created.copy(status = TournamentStatus.ACTIVE))

        val ex = assertFailsWith<TournamentNotEditableException> {
            updateTournament.update(created.id, updateRequest(name = "too late"), owner)
        }

        assertEquals(ErrorCode.TOURNAMENT_NOT_EDITABLE, ex.code)
        assertEquals("Spring Classic", getTournament.get(created.id, owner).tournament.name)
    }

    @Test
    fun `update is rejected for a completed tournament`() {
        val created = create()
        repository.save(created.copy(status = TournamentStatus.COMPLETED))

        assertFailsWith<TournamentNotEditableException> {
            updateTournament.update(created.id, updateRequest(name = "too late"), owner)
        }
    }

    @Test
    fun `update rejects lowering max teams below the registered team count`() {
        val created = create()
        addTeam(created.id, name = "Team Alpha", playerOne = "Alice", playerTwo = "Bob")
        addTeam(created.id, name = "Team Beta", playerOne = "Carol", playerTwo = "Dave")

        val ex = assertFailsWith<MaxTeamsBelowTeamCountException> {
            updateTournament.update(created.id, updateRequest(maxTeams = 1), owner)
        }

        assertEquals(ErrorCode.MAX_TEAMS_BELOW_TEAM_COUNT, ex.code)
        assertEquals("maxTeams", ex.field)
        assertEquals(16, getTournament.get(created.id, owner).tournament.maxTeams, "a rejected update changes nothing")
    }

    @Test
    fun `update accepts a max teams value equal to the registered team count`() {
        val created = create()
        addTeam(created.id, name = "Team Alpha", playerOne = "Alice", playerTwo = "Bob")
        addTeam(created.id, name = "Team Beta", playerOne = "Carol", playerTwo = "Dave")

        val updated = updateTournament.update(created.id, updateRequest(maxTeams = 2), owner)

        assertEquals(2, updated.maxTeams)
    }

    @Test
    fun `raising max teams unblocks further team creation`() {
        val created = createTournament.create(TournamentWriteRequest("Small", "", 1), owner).id
        addTeam(created, name = "Team Alpha", playerOne = "Alice", playerTwo = "Bob")

        updateTournament.update(created, updateRequest(name = "Small", maxTeams = 2), owner)
        addTeam(created, name = "Team Beta", playerOne = "Carol", playerTwo = "Dave")

        assertEquals(2, teamRepository.countByTournament(created))
    }

    @Test
    fun `a foreign admin cannot update another admin's tournament`() {
        val created = create()

        assertFailsWith<NotFoundException> {
            updateTournament.update(created.id, updateRequest(name = "hijacked"), intruder)
        }
        assertEquals("Spring Classic", getTournament.get(created.id, owner).tournament.name)
    }

    @Test
    fun `updating a missing tournament fails`() {
        assertFailsWith<NotFoundException> {
            updateTournament.update(TournamentId("nope"), updateRequest(), owner)
        }
    }

    @Test
    fun `delete removes the caller's own tournament`() {
        val created = create()

        deleteTournament.delete(created.id, owner)

        assertNull(repository.findById(created.id))
        assertTrue(listTournaments.list(owner).isEmpty())
        assertFailsWith<NotFoundException> { getTournament.get(created.id, owner) }
    }

    @Test
    fun `delete is allowed regardless of the tournament status`() {
        val created = create()
        repository.save(created.copy(status = TournamentStatus.ACTIVE))

        deleteTournament.delete(created.id, owner)

        assertNull(repository.findById(created.id))
    }

    @Test
    fun `a foreign admin cannot delete another admin's tournament`() {
        val created = create()

        assertFailsWith<NotFoundException> { deleteTournament.delete(created.id, intruder) }

        assertEquals(created, repository.findById(created.id))
    }

    @Test
    fun `deleting a missing tournament fails`() {
        assertFailsWith<NotFoundException> { deleteTournament.delete(TournamentId("nope"), owner) }
    }

    private fun admin(id: AdminUserId, email: String) = AdminUser(
        id = id,
        email = email,
        passwordHash = "hash",
        status = AdminStatus.ACTIVE,
        role = AdminRole.ADMIN,
        createdAt = Instant.parse("2026-01-01T09:00:00Z"),
    )
}
