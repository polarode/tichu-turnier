package tichu.infra.persistence

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.domain.TournamentRepository
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.insertAdminRow
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
            settings = TournamentSettings(grandTichuBonus = 100),
        )

        val stored = tournamentRepository.save(tournament)
        val loaded = tournamentRepository.findById(TournamentId("t-1"))

        assertEquals(tournament, stored)
        assertEquals(tournament, loaded)
        assertEquals(owner, loaded?.ownerId)
        assertEquals(TournamentSettings(grandTichuBonus = 100), loaded?.settings)
        assertEquals(TournamentStatus.SETUP, loaded?.status)
    }

    @Test
    fun `settings round-trip through the JSONB column`() {
        tournamentRepository.save(tournament(id = "t-1", settings = TournamentSettings(grandTichuBonus = 100)))

        // Stored as a real JSON document, not a flattened column set.
        val rawSettings = jdbcTemplate.queryForObject("SELECT settings::text FROM tournaments WHERE id = 't-1'", String::class.java)
        assertEquals("""{"totalRounds": 4, "doubleWinBonus": 200, "grandTichuBonus": 100}""", rawSettings)

        // And the adapter maps it back into the domain value object.
        val freshAdapter = JpaTournamentRepository(springDataTournamentRepository)
        assertEquals(TournamentSettings(grandTichuBonus = 100), freshAdapter.findById(TournamentId("t-1"))?.settings)
    }

    @Test
    fun `V17 turns the legacy grand tichu toggle into the matching bonus`() {
        val legacy = mapOf("t-off" to """{"allowGrandTichu": false}""", "t-on" to """{"allowGrandTichu": true}""", "t-unset" to "{}")
        for ((id, settings) in legacy) {
            jdbcTemplate.update(
                "INSERT INTO tournaments (id, owner_id, name, description, max_teams, settings, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'Legacy', '', 16, ?::jsonb, 'SETUP', now(), now())",
                id, owner.value, settings,
            )
        }

        val migration = javaClass.getResource("/db/migration/V17__grand_tichu_bonus_setting.sql")!!.readText()
        jdbcTemplate.execute(migration)

        assertEquals(TournamentSettings(grandTichuBonus = 100), tournamentRepository.findById(TournamentId("t-off"))?.settings)
        assertEquals(TournamentSettings(grandTichuBonus = 200), tournamentRepository.findById(TournamentId("t-on"))?.settings)
        assertEquals(TournamentSettings(grandTichuBonus = 200), tournamentRepository.findById(TournamentId("t-unset"))?.settings)
    }

    @Test
    fun `V18 gives tournaments without total rounds the default 4 and keeps a stored value`() {
        // Raw JSON is asserted: reading through the adapter would hide a missing key behind the domain default.
        val stored = mapOf("t-old" to """{"grandTichuBonus": 100}""", "t-set" to """{"grandTichuBonus": 200, "totalRounds": 6}""")
        for ((id, settings) in stored) {
            jdbcTemplate.update(
                "INSERT INTO tournaments (id, owner_id, name, description, max_teams, settings, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'Stored', '', 16, ?::jsonb, 'ACTIVE', now(), now())",
                id, owner.value, settings,
            )
        }

        val migration = javaClass.getResource("/db/migration/V18__total_rounds_setting.sql")!!.readText()
        jdbcTemplate.execute(migration)

        fun rawSettings(id: String) = jdbcTemplate.queryForObject("SELECT settings::text FROM tournaments WHERE id = ?", String::class.java, id)
        assertEquals("""{"totalRounds": 4, "grandTichuBonus": 100}""", rawSettings("t-old"))
        assertEquals("""{"totalRounds": 6, "grandTichuBonus": 200}""", rawSettings("t-set"))
    }

    @Test
    fun `V19 gives tournaments without a double win bonus the default 200 and keeps a stored value`() {
        // Raw JSON is asserted: reading through the adapter would hide a missing key behind the domain default.
        val stored = mapOf(
            "t-old" to """{"grandTichuBonus": 100, "totalRounds": 4}""",
            "t-set" to """{"grandTichuBonus": 200, "totalRounds": 6, "doubleWinBonus": 100}""",
        )
        for ((id, settings) in stored) {
            jdbcTemplate.update(
                "INSERT INTO tournaments (id, owner_id, name, description, max_teams, settings, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'Stored', '', 16, ?::jsonb, 'ACTIVE', now(), now())",
                id, owner.value, settings,
            )
        }

        val migration = javaClass.getResource("/db/migration/V19__double_win_bonus_setting.sql")!!.readText()
        jdbcTemplate.execute(migration)

        fun rawSettings(id: String) = jdbcTemplate.queryForObject("SELECT settings::text FROM tournaments WHERE id = ?", String::class.java, id)
        assertEquals("""{"totalRounds": 4, "doubleWinBonus": 200, "grandTichuBonus": 100}""", rawSettings("t-old"))
        assertEquals("""{"totalRounds": 6, "doubleWinBonus": 100, "grandTichuBonus": 200}""", rawSettings("t-set"))
    }

    @Test
    fun `public visibility round-trips through its own column and defaults to false`() {
        tournamentRepository.save(tournament(id = "t-public", publicVisibility = true))
        tournamentRepository.save(tournament(id = "t-private"))

        assertEquals(true, tournamentRepository.findById(TournamentId("t-public"))?.publicVisibility)
        assertEquals(false, tournamentRepository.findById(TournamentId("t-private"))?.publicVisibility)

        // Stored in the dedicated boolean column, not inside the JSONB settings document.
        val rawFlag = jdbcTemplate.queryForObject("SELECT public_visibility FROM tournaments WHERE id = 't-public'", Boolean::class.java)
        assertEquals(true, rawFlag)
        assertFalse(jdbcTemplate.queryForObject("SELECT settings::text FROM tournaments WHERE id = 't-public'", String::class.java)!!.contains("publicVisibility"))
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
        publicVisibility: Boolean = false,
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
        publicVisibility = publicVisibility,
    )
}
