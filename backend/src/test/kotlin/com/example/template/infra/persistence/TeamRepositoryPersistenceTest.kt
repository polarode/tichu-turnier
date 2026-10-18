package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Player
import com.example.template.domain.PlayerId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.vocabulary.DuplicatePlayerNameException
import com.example.template.vocabulary.DuplicateTeamNameException
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * Repository-port integration test: the [TeamRepository] adapter must run against
 * real PostgreSQL (Testcontainers). Deliberately does NOT use test transactions —
 * each repository call must commit, so the tests prove data actually reaches the
 * database, that the aggregate save writes both tables, and that the database
 * constraints (tournament-scoped uniqueness, cascading deletes) hold.
 */
@SpringBootTest(classes = [Application::class])
class TeamRepositoryPersistenceTest(
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val springDataTeamRepository: SpringDataTeamRepository,
    @Autowired private val springDataPlayerRepository: SpringDataPlayerRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private var tournamentSeq = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        // tournaments.owner_id is a real FK, so the owner must exist before saving.
        jdbcTemplate.update(
            "INSERT INTO admin_users (id, email, password_hash, status, role, created_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', 'ADMIN', now()) ON CONFLICT DO NOTHING",
            "admin-owner",
            "owner@example.com",
            "irrelevant-hash",
        )
    }

    private fun insertTournament(): TournamentId {
        tournamentSeq += 1
        val id = "t-$tournamentSeq"
        jdbcTemplate.update(
            "INSERT INTO tournaments (id, owner_id, name, description, max_teams, settings, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, '', 16, '{}', 'SETUP', now(), now())",
            id,
            "admin-owner",
            "Tournament $tournamentSeq",
        )
        return TournamentId(id)
    }

    private fun team(
        id: String,
        tournamentId: TournamentId,
        name: String = "Team Alpha",
        playerOneName: String = "Alice",
        playerTwoName: String = "Bob",
    ): Team {
        val playerOne = Player(
            id = PlayerId("$id-p1"),
            tournamentId = tournamentId,
            teamId = TeamId(id),
            name = playerOneName,
        )
        val playerTwo = Player(
            id = PlayerId("$id-p2"),
            tournamentId = tournamentId,
            teamId = TeamId(id),
            name = playerTwoName,
        )
        return Team(id = TeamId(id), tournamentId = tournamentId, name = name, players = listOf(playerOne, playerTwo))
    }

    @Test
    fun `saved team is read back with all fields intact including its players`() {
        val tournamentId = insertTournament()
        val team = team("t1-team-1", tournamentId)

        val stored = teamRepository.save(team)
        val loaded = teamRepository.findById(TeamId("t1-team-1"), tournamentId)

        assertEquals(team, stored)
        assertEquals(team, loaded)
        assertEquals(listOf("Alice", "Bob"), loaded?.players?.map { it.name })
        assertEquals(tournamentId, loaded?.tournamentId)
    }

    @Test
    fun `data lives in the database, not in a repository cache`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t2-team-1", tournamentId, name = "Survives"))

        val rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM teams", Long::class.java)
        assertEquals(1L, rowCount)
        val playerCount = jdbcTemplate.queryForObject("SELECT count(*) FROM players", Long::class.java)
        assertEquals(2L, playerCount, "the aggregate save wrote both player rows")

        val freshAdapter = freshAdapter()
        assertEquals("Survives", freshAdapter.findById(TeamId("t2-team-1"), tournamentId)?.name)
    }

    @Test
    fun `findAllByTournament returns only that tournament's teams`() {
        val first = insertTournament()
        val second = insertTournament()
        teamRepository.save(team("t3-team-1", first, name = "A", playerOneName = "P1", playerTwoName = "P2"))
        teamRepository.save(team("t3-team-2", first, name = "B", playerOneName = "P3", playerTwoName = "P4"))
        teamRepository.save(team("t3-team-3", second, name = "C", playerOneName = "P1", playerTwoName = "P2"))

        val mine = teamRepository.findAllByTournament(first)

        assertEquals(2, mine.size)
        assertEquals(setOf("A", "B"), mine.map { it.name }.toSet())
    }

    @Test
    fun `the same team and player names are accepted in another tournament`() {
        val first = insertTournament()
        val second = insertTournament()

        teamRepository.save(team("t4-team-1", first, name = "Team Alpha", playerOneName = "Alice", playerTwoName = "Bob"))
        teamRepository.save(team("t4-team-2", second, name = "Team Alpha", playerOneName = "Alice", playerTwoName = "Bob"))

        assertEquals(2, teamRepository.findAllByTournament(first).size + teamRepository.findAllByTournament(second).size)
    }

    @Test
    fun `a duplicate team name within the tournament violates the database constraint`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t5-team-1", tournamentId, name = "Team Alpha"))

        val duplicate = team("t5-team-2", tournamentId, name = "Team Alpha", playerOneName = "Carol", playerTwoName = "Dave")
        assertFailsWith<DuplicateTeamNameException> { teamRepository.save(duplicate) }
    }

    @Test
    fun `a duplicate player name within the tournament violates the database constraint`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t6-team-1", tournamentId, name = "Team Alpha", playerOneName = "Alice", playerTwoName = "Bob"))

        val duplicate = team("t6-team-2", tournamentId, name = "Team Beta", playerOneName = "Carol", playerTwoName = "Alice")
        val ex = assertFailsWith<DuplicatePlayerNameException> { teamRepository.save(duplicate) }
        assertEquals("playerTwoName", ex.field)
    }

    @Test
    fun `deleting the tournament removes its teams and players`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t7-team-1", tournamentId))

        jdbcTemplate.update("DELETE FROM tournaments WHERE id = ?", tournamentId.value)

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM teams", Long::class.java))
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM players", Long::class.java))
        assertNull(teamRepository.findById(TeamId("t7-team-1"), tournamentId))
    }

    @Test
    fun `deleting a team removes its players`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t8-team-1", tournamentId))

        teamRepository.delete(TeamId("t8-team-1"), tournamentId)

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM teams", Long::class.java))
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM players", Long::class.java))
    }

    @Test
    fun `findById with the wrong tournament id returns null`() {
        val first = insertTournament()
        val second = insertTournament()
        teamRepository.save(team("t9-team-1", first))

        assertNull(teamRepository.findById(TeamId("t9-team-1"), second))
    }

    @Test
    fun `countByTournament counts only that tournament's teams`() {
        val first = insertTournament()
        val second = insertTournament()
        teamRepository.save(team("t10-team-1", first, name = "A", playerOneName = "P1", playerTwoName = "P2"))
        teamRepository.save(team("t10-team-2", first, name = "B", playerOneName = "P3", playerTwoName = "P4"))
        teamRepository.save(team("t10-team-3", second, name = "C", playerOneName = "P5", playerTwoName = "P6"))

        assertEquals(2, teamRepository.countByTournament(first))
        assertEquals(1, teamRepository.countByTournament(second))
    }

    @Test
    fun `saving an existing team renames it and replaces its players`() {
        val tournamentId = insertTournament()
        teamRepository.save(team("t11-team-1", tournamentId, name = "Old Name", playerOneName = "Alice", playerTwoName = "Bob"))

        val updated = team("t11-team-1", tournamentId, name = "New Name", playerOneName = "Carol", playerTwoName = "Dave")
        teamRepository.save(updated)

        val loaded = teamRepository.findById(TeamId("t11-team-1"), tournamentId)
        assertEquals(updated, loaded)
        assertEquals(listOf("Carol", "Dave"), loaded?.players?.map { it.name })
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT count(*) FROM players", Long::class.java), "old players are not orphaned")
        assertEquals(
            0L,
            jdbcTemplate.queryForObject("SELECT count(*) FROM players WHERE name IN ('Alice', 'Bob')", Long::class.java),
            "the previous player names are gone",
        )
    }

    private fun freshAdapter(): TeamRepository =
        // Rebuild the adapter over the shared Spring Data repositories so the read
        // bypasses any JPA first-level cache and proves the row was committed.
        JpaTeamRepository(springDataTeamRepository, springDataPlayerRepository)
}
