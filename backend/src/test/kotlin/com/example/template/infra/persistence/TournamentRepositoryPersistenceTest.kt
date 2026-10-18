package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.domain.TournamentRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-port integration test: the [TournamentRepository] adapter must run
 * against real PostgreSQL (Testcontainers). Deliberately does NOT use test
 * transactions — each repository call must commit, so the tests prove data actually
 * reaches the database and survives beyond a single repository instance.
 */
@SpringBootTest(classes = [Application::class])
class TournamentRepositoryPersistenceTest(
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val springDataTournamentRepository: SpringDataTournamentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val owner = AdminUserId("admin-owner")
    private val otherOwner = AdminUserId("admin-other")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        // tournaments.owner_id is a real FK, so the owners must exist before saving.
        insertAdminRow(jdbcTemplate, owner.value, "owner@example.com")
        insertAdminRow(jdbcTemplate, otherOwner.value, "other@example.com")
    }

    @Test
    fun `saved tournament is read back with all fields intact including its owner and settings`() {
        val tournament = tournament(
            id = "t-1",
            name = "Spring Classic",
            settings = TournamentSettings(allowGrandTichu = false),
        )

        val stored = tournamentRepository.save(tournament)
        val loaded = tournamentRepository.findById(TournamentId("t-1"))

        assertEquals(tournament, stored)
        assertEquals(tournament, loaded)
        assertEquals(owner, loaded?.ownerId)
        assertEquals(TournamentSettings(allowGrandTichu = false), loaded?.settings)
        assertEquals(TournamentStatus.SETUP, loaded?.status)
    }

    @Test
    fun `settings round-trip through the JSONB column`() {
        tournamentRepository.save(tournament(id = "t-1", settings = TournamentSettings(allowGrandTichu = false)))

        // Stored as a real JSON document, not a flattened column set.
        val rawSettings = jdbcTemplate.queryForObject("SELECT settings::text FROM tournaments WHERE id = 't-1'", String::class.java)
        assertEquals("""{"allowGrandTichu": false}""", rawSettings)

        // And the adapter maps it back into the domain value object.
        val freshAdapter = JpaTournamentRepository(springDataTournamentRepository)
        assertEquals(TournamentSettings(allowGrandTichu = false), freshAdapter.findById(TournamentId("t-1"))?.settings)
    }

    @Test
    fun `data lives in the database, not in a repository cache`() {
        tournamentRepository.save(tournament(id = "t-1", name = "Survives"))

        // A brand-new adapter instance over the same EntityManager must find the
        // row — proving it was committed to Postgres, not held in memory.
        val freshAdapter = JpaTournamentRepository(springDataTournamentRepository)
        val loaded = freshAdapter.findById(TournamentId("t-1"))

        assertEquals("Survives", loaded?.name)

        val rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tournaments", Long::class.java)
        assertEquals(1L, rowCount)
    }

    @Test
    fun `findAllByOwner returns only that owner's tournaments`() {
        tournamentRepository.save(tournament(id = "t-1", name = "A", ownerId = owner))
        tournamentRepository.save(tournament(id = "t-2", name = "B", ownerId = owner))
        tournamentRepository.save(tournament(id = "t-3", name = "C", ownerId = otherOwner))

        val mine = tournamentRepository.findAllByOwner(owner)

        assertEquals(2, mine.size)
        assertTrue(mine.all { it.ownerId == owner })
        assertFalse(mine.any { it.id.value == "t-3" })
    }

    @Test
    fun `delete removes the row from the database`() {
        tournamentRepository.save(tournament(id = "t-1"))

        tournamentRepository.delete(TournamentId("t-1"))

        assertNull(tournamentRepository.findById(TournamentId("t-1")))
        val rowCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tournaments WHERE id = 't-1'",
            Long::class.java,
        )
        assertEquals(0L, rowCount, "the delete is committed to Postgres")
    }

    private fun tournament(
        id: String,
        name: String = "Spring Classic",
        ownerId: AdminUserId = owner,
        settings: TournamentSettings = TournamentSettings(),
    ): Tournament = Tournament(
        id = TournamentId(id),
        ownerId = ownerId,
        name = name,
        description = "",
        maxTeams = 16,
        settings = settings,
        status = TournamentStatus.SETUP,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
