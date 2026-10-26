package com.example.template.application.usecase

import com.example.template.application.api.TeamWriteRequest
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentStatus
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.testsupport.InMemoryRoundRepository
import com.example.template.testsupport.InMemoryTeamRepository
import com.example.template.testsupport.InMemoryTeamTokenRepository
import com.example.template.testsupport.RecordingSessionTokens
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.InsufficientTeamsException
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.PairingUnsupportedException
import com.example.template.vocabulary.TournamentNotStartableException
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

    private val createTeam = CreateTeamCommand(
        tournamentRepository,
        teamRepository,
        InMemoryTeamTokenRepository(),
        RecordingSessionTokens(),
        idGenerator,
        fixedClock,
    )
    private val startTournament = StartTournamentCommand(
        tournamentRepository,
        teamRepository,
        roundRepository,
        idGenerator,
        Random(42),
        fixedClock,
    )

    private fun createTournament(): TournamentId {
        val tournament = com.example.template.application.usecase.CreateTournamentCommand(
            tournamentRepository,
            idGenerator,
            fixedClock,
        ).create(
            com.example.template.application.api.TournamentWriteRequest("Spring Classic", "", 16),
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
        val teamIds = started.round.pairing.tables.flatMap { listOf(it.home, it.away) }
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
            com.example.template.application.usecase.CreateTournamentCommand(tournaments, idGenerator, fixedClock)
                .create(
                    com.example.template.application.api.TournamentWriteRequest("Spring Classic", "", 16),
                    owner,
                )
            val id = TournamentId("id-1")
            CreateTeamCommand(tournaments, teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Alpha", "A1", "A2"), owner)
            CreateTeamCommand(tournaments, teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Beta", "B1", "B2"), owner)
            CreateTeamCommand(tournaments, teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Gamma", "G1", "G2"), owner)
            CreateTeamCommand(tournaments, teams, InMemoryTeamTokenRepository(), RecordingSessionTokens(), idGenerator, fixedClock)
                .create(id, TeamWriteRequest("Team Delta", "D1", "D2"), owner)
            val start = StartTournamentCommand(tournaments, teams, rounds, idGenerator, Random(seed), fixedClock)
            val started = start.start(id, owner)
            return started.round.pairing.tables.map { table ->
                teams.findById(table.home)!!.name to teams.findById(table.away)!!.name
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
    }

    @Test
    fun `an odd team count is rejected with the temporary unsupported error`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        addTeam(id, "Team Gamma")

        val ex = assertFailsWith<PairingUnsupportedException> { startTournament.start(id, owner) }

        assertEquals(ErrorCode.PAIRING_UNSUPPORTED, ex.code)
        assertEquals(TournamentStatus.SETUP, tournamentRepository.findById(id)?.status, "no side effects")
        assertEquals(0, roundRepository.findByTournament(id).size)
    }

    @Test
    fun `a failure while persisting the round rolls the tournament back to setup`() {
        val id = createTournament()
        addTeam(id, "Team Alpha")
        addTeam(id, "Team Beta")
        val failingRounds = object :
            com.example.template.domain.RoundRepository by roundRepository {
            override fun save(round: com.example.template.domain.Round): Nothing =
                throw IllegalStateException("injected persistence failure")
        }
        val failingStart = StartTournamentCommand(
            tournamentRepository,
            teamRepository,
            failingRounds,
            idGenerator,
            Random(42),
            fixedClock,
        )

        val ex = assertFailsWith<IllegalStateException> { failingStart.start(id, owner) }

        assertEquals("injected persistence failure", ex.message)
        assertEquals(TournamentStatus.SETUP, tournamentRepository.findById(id)?.status)
        assertTrue(roundRepository.findByTournament(id).isEmpty(), "no partial round was persisted")
    }
}
