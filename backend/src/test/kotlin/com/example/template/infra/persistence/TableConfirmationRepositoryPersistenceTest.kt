package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Player
import com.example.template.domain.PlayerId
import com.example.template.domain.TableConfirmationRepository
import com.example.template.domain.TableId
import com.example.template.domain.TableKey
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
import com.example.template.testsupport.insertAdminRow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repository-port integration test: the [TableConfirmationRepository] adapter
 * against real PostgreSQL (Testcontainers). No test transactions — each call must
 * commit, so the tests prove the rows reach the database.
 */
@SpringBootTest(classes = [Application::class])
class TableConfirmationRepositoryPersistenceTest(
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val owner = AdminUserId("admin-owner")
    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val teamC = TeamId("team-c")
    private val at = Instant.parse("2026-01-01T13:00:00Z")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        insertAdminRow(jdbcTemplate, owner.value, "[EMAIL]")
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = owner,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = at,
                updatedAt = at,
            ),
        )
        listOf(teamA, teamB, teamC).forEach { id ->
            teamRepository.save(
                Team(
                    id = id,
                    tournamentId = tournamentId,
                    name = "Team ${id.value}",
                    players = listOf(
                        Player(PlayerId("${id.value}-p1"), tournamentId, id, "P1 ${id.value}"),
                        Player(PlayerId("${id.value}-p2"), tournamentId, id, "P2 ${id.value}"),
                    ),
                ),
            )
        }
    }

    private fun table(roundNumber: Int, tableNumber: Int) = TableKey(tournamentId, roundNumber, tableNumber)

    @Test
    fun `added confirmations are read back per table`() {
        confirmationRepository.add(table(1, 1), teamA, at)
        confirmationRepository.add(table(1, 1), teamB, at)
        confirmationRepository.add(table(1, 2), teamC, at)

        assertEquals(setOf(teamA, teamB), confirmationRepository.findByTable(table(1, 1)))
        assertEquals(setOf(teamC), confirmationRepository.findByTable(table(1, 2)))
        assertEquals(emptySet(), confirmationRepository.findByTable(table(2, 1)))
    }

    @Test
    fun `a round's confirmations are grouped by table and leave out other rounds`() {
        confirmationRepository.add(table(1, 1), teamA, at)
        confirmationRepository.add(table(1, 2), teamC, at)
        confirmationRepository.add(table(2, 1), teamB, at)

        assertEquals(
            mapOf(TableId(1) to setOf(teamA), TableId(2) to setOf(teamC)),
            confirmationRepository.findByRound(tournamentId, 1),
        )
    }

    @Test
    fun `removing deletes only that team's confirmation and is a no-op when absent`() {
        confirmationRepository.add(table(1, 1), teamA, at)
        confirmationRepository.add(table(1, 1), teamB, at)

        confirmationRepository.remove(table(1, 1), teamA)
        confirmationRepository.remove(table(1, 1), teamC)

        assertEquals(setOf(teamB), confirmationRepository.findByTable(table(1, 1)))
    }

    @Test
    fun `deleting the tournament cascades to its confirmations`() {
        confirmationRepository.add(table(1, 1), teamA, at)

        tournamentRepository.delete(tournamentId)

        assertTrue(confirmationRepository.findByTable(table(1, 1)).isEmpty())
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM table_confirmations", Int::class.java))
    }
}
