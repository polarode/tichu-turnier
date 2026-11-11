package tichu.application.usecase

import tichu.testsupport.InMemoryAdminUserRepository
import tichu.application.api.TeamWriteRequest
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.InMemoryTeamTokenRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.testsupport.RecordingSessionTokens
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.InsufficientTeamsException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotStartableException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pure unit test of the start-tournament use case: no Spring context. Ports are
 * satisfied with in-memory repositories, a fixed clock, a counting id fake and a
 * seeded `Random`, so the shuffle is fully reproducible.
 *
 * Seams proven here: the status transition, round 1 creation with table assignment,
 * every typed failure (with the repository state unchanged), the odd-team temporary
 * contract, and — via a failing round repository — that a partial failure leaves the
 * tournament in `SETUP`.
 */
class StartTournamentCommandTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${++counter}"
    }
    private val owner = AdminUserId("admin-owner")
    private val startEvents = RecordingTournamentEvents()

    private val createTeam = CreateTeamCommand(
        TournamentAccess(tournamentRepository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()),
        teamRepository,
        InMemoryTeamTokenRepository(),
        RecordingSessionTokens(),
        idGenerator,
        fixedClock,
    )
    private val startTournament = StartTournamentCommand(
        tournamentRepository,
        TournamentAccess(tournamentRepository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()),
        teamRepository,
        roundRepository,
        idGenerator,
        Random(42),
        fixedClock,
        startEvents,
    )

    private fun createTournament(): TournamentId {
        val tournament = tichu.application.usecase.CreateTournamentCommand(
            tournamentRepository,
            idGenerator,
            fixedClock,
        ).create(
            tichu.application.api.TournamentWriteRequest("Spring Classic", "", 16),
            owner,
        )
        return tournament.id
    }

    private fun addTeam(tournamentId: TournamentId, name: String) {
        createTeam.create(tournamentId, TeamWriteRequest(name, "P1 of $name", "P2 of $name"), owner)
    }

    @Test
    fun `starting a tournament activates it and creates round 1 with sequentially numbered tables`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        addTeam(id, "Team Gamma")
        addTeam(id, "Team Delta")

        val started = startTournament.start(id, owner)

        assertEquals(TournamentStatus.ACTIVE, started.tournament.status)
        assertEquals(1, started.round.number)
        assertEquals(listOf(1, 2), started.round.pairing.tables.map { it.id.value })
        val teamIds = started.round.pairing.tables.flatMap { it.seatedTeams }
        assertEquals(4, teamIds.distinct().size, "every team sits at exactly one table")
    }

    @Test
    fun `round 1 pairing is deterministic for a given seed and varies across seeds`() {
        fun pairingForSeed(seed: Int): List<Pair<String, String>> {
            val idGenerator = object : IdGenerator {
                private var counter = 0
                override fun newId(): String = "id-${++counter}"
            }
            val tournaments = InMemoryTournamentRepository()
            val teams = InMemoryTeamRepository()
            val rounds = InMemoryRoundRepository()
            tichu.application.usecase.CreateTournamentCommand(tournaments, idGenerator, fixedClock)
                .create(
                    tichu.application.api.TournamentWriteRequest("Spring Classic", "", 16),
                    owner,
                )
            val id = TournamentId("id-1")
            CreateTeamCommand(TournamentAccess(tournaments, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Alpha", "A1", "A2"), owner)
            CreateTeamCommand(TournamentAccess(tournaments, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Beta", "B1", "B2"), owner)
            CreateTeamCommand(TournamentAccess(tournaments, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Gamma", "G1", "G2"), owner)
            CreateTeamCommand(TournamentAccess(tournaments, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Delta", "D1", "D2"), owner)
            val start = StartTournamentCommand(tournaments, TournamentAccess(tournaments, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()), teams, rounds, idGenerator, Random(seed), fixedClock, RecordingTournamentEvents())
            val started = start.start(id, owner)
            return started.round.pairing.tables.map { table ->
                teams.findById(table.seatedTeams[0])!!.name to teams.findById(table.seatedTeams[1])!!.name
            }
        }

        val first = pairingForSeed(42)
        assertEquals(first, pairingForSeed(42), "same seed reproduces the same pairing")
        assertTrue(
            (1..5).map { pairingForSeed(it) }.distinct().size > 1,
            "different seeds produce different shuffles",
        )
    }

    @Test
    fun `round 1 is stored under round number 1`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")

        startTournament.start(id, owner)

        assertEquals(1, roundRepository.findByTournamentAndNumber(id, 1)?.number)
    }

    @Test
    fun `starting an already started tournament fails typed and changes nothing`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        startTournament.start(id, owner)

        val ex = assertFailsWith<TournamentNotStartableException> { startTournament.start(id, owner) }

        assertEquals(ErrorCode.TOURNAMENT_NOT_STARTABLE, ex.code)
        assertEquals(1, roundRepository.findByTournament(id).size, "no additional round was created")
    }

    @Test
    fun `starting with fewer than two teams fails typed and changes nothing`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")

        val ex = assertFailsWith<InsufficientTeamsException> { startTournament.start(id, owner) }

        assertEquals(ErrorCode.INSUFFICIENT_TEAMS, ex.code)
        assertEquals(0, roundRepository.findByTournament(id).size)
        assertEquals(TournamentStatus.SETUP, tournamentRepository.findById(id)?.status)
    }

    @Test
    fun `starting a completed tournament fails typed`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        startTournament.start(id, owner)
        tournamentRepository.save(tournamentRepository.findById(id)!!.copy(status = TournamentStatus.COMPLETED))

        val ex = assertFailsWith<TournamentNotStartableException> { startTournament.start(id, owner) }

        assertEquals(ErrorCode.TOURNAMENT_NOT_STARTABLE, ex.code)
    }

    @Test
    fun `starting another admin's tournament looks not found`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")

        assertFailsWith<NotFoundException> { startTournament.start(id, AdminUserId("admin-intruder")) }
        assertEquals(emptyList(), startEvents.published)
    }

    // ---- live events ------------------------------------------------------------

    @Test
    fun `starting a tournament publishes one tournament-started event for round 1`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")

        startTournament.start(id, owner)

        val round1 = roundRepository.findByTournamentAndNumber(id, 1)!!
        assertEquals(
            listOf(TournamentEvent(TournamentEventType.TOURNAMENT_STARTED, id, round1.id)),
            startEvents.published,
        )
    }

    @Test
    fun `starting an already started tournament publishes nothing`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        startTournament.start(id, owner)
        startEvents.published.clear()

        assertFailsWith<TournamentNotStartableException> { startTournament.start(id, owner) }

        assertEquals(emptyList(), startEvents.published)
    }

    @Test
    fun `starting with fewer than two teams publishes nothing`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")

        assertFailsWith<InsufficientTeamsException> { startTournament.start(id, owner) }

        assertEquals(emptyList(), startEvents.published)
    }

    @Test
    fun `an odd team count starts with a trio table at the last table number`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        addTeam(id, "Team Gamma")

        val started = startTournament.start(id, owner)

        assertEquals(TournamentStatus.ACTIVE, started.tournament.status)
        val tables = roundRepository.findByTournamentAndNumber(id, 1)!!.pairing.tables
        assertEquals(1, tables.size)
        assertTrue(tables.single().isTrio)
    }

    @Test
    fun `persists nothing when the round save fails (ordering, not Spring rollback)`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        val failingRounds = object :
            tichu.domain.RoundRepository by roundRepository {
            override fun save(round: tichu.domain.Round): Nothing =
                throw IllegalStateException("injected persistence failure")
        }
        val failingStart = StartTournamentCommand(
            tournamentRepository,
            TournamentAccess(tournamentRepository, InMemoryCoOrganizerRepository(), InMemoryAdminUserRepository()),
            teamRepository,
            failingRounds,
            idGenerator,
            Random(42),
            fixedClock,
            RecordingTournamentEvents(),
        )

        val ex = assertFailsWith<IllegalStateException> { failingStart.start(id, owner) }

        assertEquals("injected persistence failure", ex.message)
        assertEquals(TournamentStatus.SETUP, tournamentRepository.findById(id)?.status)
        assertTrue(roundRepository.findByTournament(id).isEmpty(), "no partial round was persisted")
    }
}
