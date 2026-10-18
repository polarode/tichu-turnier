package com.example.template.application.usecase

import com.example.template.application.api.CreateTournament
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.NotFoundException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    private val repository = InMemoryTournamentRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "tournament-${++counter}"
    }

    private val owner = AdminUserId("admin-owner")
    private val intruder = AdminUserId("admin-intruder")

    private val createTournament = CreateTournamentCommand(repository, idGenerator, fixedClock)
    private val listTournaments = ListTournamentsQuery(repository)
    private val getTournament = GetTournamentQuery(repository)

    private fun create(name: String = "Spring Classic", asAdmin: AdminUserId = owner) =
        createTournament.create(
            CreateTournament.CreateTournamentRequest(name, "", 16),
            asAdmin,
        )

    @Test
    fun `creates a tournament in setup status with generated id and the caller as owner`() {
        val tournament = createTournament.create(
            CreateTournament.CreateTournamentRequest(
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
                CreateTournament.CreateTournamentRequest("   ", "", 16),
                owner,
            )
        }
        assertEquals("name", ex.field)
    }

    @Test
    fun `rejects a name longer than 255 characters anchored to the name field`() {
        val ex = assertFailsWith<InvalidFieldException> {
            createTournament.create(
                CreateTournament.CreateTournamentRequest("x".repeat(256), "", 16),
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
                    CreateTournament.CreateTournamentRequest("Classic", "", maxTeams),
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

}
