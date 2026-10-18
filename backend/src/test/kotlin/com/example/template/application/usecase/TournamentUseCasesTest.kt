package com.example.template.application.usecase

import com.example.template.application.api.TeamWriteRequest
import com.example.template.application.api.TournamentWriteRequest
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.testsupport.InMemoryTeamRepository
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.MaxTeamsBelowTeamCountException
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.TournamentNotEditableException
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
    private val listTournaments = ListTournamentsQuery(repository)
    private val getTournament = GetTournamentQuery(repository)
    private val updateTournament = UpdateTournamentCommand(repository, teamRepository, laterClock)
    private val deleteTournament = DeleteTournamentCommand(repository)
    private val createTeam = CreateTeamCommand(repository, teamRepository, idGenerator)

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
                settings = TournamentSettings(allowGrandTichu = false),
            ),
            owner,
        )

        assertEquals(TournamentId("tournament-1"), tournament.id)
        assertEquals(owner, tournament.ownerId, "the creating admin is recorded as owner")
        assertEquals("Spring Classic", tournament.name, "name is trimmed")
        assertEquals("annual invitational", tournament.description, "description is trimmed")
        assertEquals(16, tournament.maxTeams)
        assertEquals(TournamentSettings(allowGrandTichu = false), tournament.settings)
        assertEquals(TournamentStatus.SETUP, tournament.status, "new tournaments start in setup")
        assertEquals(fixedClock.instant(), tournament.createdAt)
        assertEquals(fixedClock.instant(), tournament.updatedAt)
    }

    @Test
    fun `defaults the grand tichu setting to allowed`() {
        val tournament = create()

        assertEquals(TournamentSettings(allowGrandTichu = true), tournament.settings)
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
    fun `owner reads their own tournament`() {
        val created = create()

        assertEquals(created, getTournament.get(created.id, owner))
    }

    @Test
    fun `listing returns only the caller's own tournaments`() {
        val mine = create("mine")
        val alsoMine = create("also mine")
        val theirs = create("theirs", asAdmin = intruder)

        val ownerIds = listTournaments.list(owner).map { it.id }.toSet()
        val intruderIds = listTournaments.list(intruder).map { it.id }.toSet()

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
                settings = TournamentSettings(allowGrandTichu = false),
            ),
            owner,
        )

        assertEquals(created.id, updated.id, "the id is stable")
        assertEquals(owner, updated.ownerId, "the owner is stable")
        assertEquals("Renamed Classic", updated.name, "name is trimmed")
        assertEquals("new description", updated.description, "description is trimmed")
        assertEquals(8, updated.maxTeams)
        assertEquals(TournamentSettings(allowGrandTichu = false), updated.settings)
        assertEquals(TournamentStatus.SETUP, updated.status, "update does not change status")
        assertEquals(created.createdAt, updated.createdAt, "createdAt is preserved")
        assertEquals(laterClock.instant(), updated.updatedAt, "updatedAt is bumped")
        assertEquals(updated, getTournament.get(created.id, owner), "the update is persisted")
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
    fun `update is rejected once the tournament has started`() {
        val created = create()
        repository.save(created.copy(status = TournamentStatus.ACTIVE))

        val ex = assertFailsWith<TournamentNotEditableException> {
            updateTournament.update(created.id, updateRequest(name = "too late"), owner)
        }

        assertEquals(ErrorCode.TOURNAMENT_NOT_EDITABLE, ex.code)
        assertEquals("Spring Classic", getTournament.get(created.id, owner).name)
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
        assertEquals(16, getTournament.get(created.id, owner).maxTeams, "a rejected update changes nothing")
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
        assertEquals("Spring Classic", getTournament.get(created.id, owner).name)
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

}
