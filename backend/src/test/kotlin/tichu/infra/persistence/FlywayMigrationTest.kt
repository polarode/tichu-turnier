package tichu.infra.persistence

import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.insertAdminRow
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Migration-step test: a single Flyway migration is the unit under test, and its public
 * interface is SQL in, rows out. Each test gets its own fresh PostgreSQL schema, migrates
 * to the version before the step, inserts legacy rows with plain SQL, migrates to the step,
 * and asserts the resulting rows. No domain adapter and no Spring context are involved.
 *
 * Full-chain apply and entity/schema agreement are already covered elsewhere: every
 * `@SpringBootTest` runs all migrations, and `ddl-auto: validate` fails the boot when the
 * schema drifts from the entities. This class covers the data-touching migrations step by
 * step, and the data rules (CHECK constraints) a migration adds, which those tests cannot do
 * because they only ever see the final state and write through the entities.
 */
class FlywayMigrationTest {

    // A fresh schema per test instance (JUnit creates one instance per test by default),
    // so tests never see each other's rows and never touch the Spring tests' public schema.
    private val schema = "migration_" + UUID.randomUUID().toString().replace("-", "").take(12)
    private val dataSource = PostgresBackedIntegrationTest.postgres.let {
        DriverManagerDataSource(withCurrentSchema(it.jdbcUrl, schema), it.username, it.password)
    }
    private val jdbc = JdbcTemplate(dataSource)

    @AfterEach
    fun dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
    }

    @Test
    fun `V6 backfills existing admins to ACTIVE and ADMIN`() {
        migrateTo("5")
        jdbc.update(
            "INSERT INTO admin_users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
            "admin-legacy",
            "legacy@example.com",
            "hash",
            Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")),
        )

        migrateTo("6")

        val row = jdbc.queryForMap("SELECT status, role FROM admin_users WHERE id = ?", "admin-legacy")
        assertEquals("ACTIVE", row["status"])
        assertEquals("ADMIN", row["role"])
    }

    @Test
    fun `V17 turns the legacy grand tichu toggle into the matching bonus`() {
        migrateTo("16")
        insertTournament("t-off", """{"allowGrandTichu": false}""")
        insertTournament("t-on", """{"allowGrandTichu": true}""")
        insertTournament("t-unset", "{}")
        insertTournament("t-done", """{"allowGrandTichu": false, "grandTichuBonus": 200}""")

        migrateTo("17")

        assertEquals("""{"grandTichuBonus": 100}""", rawSettings("t-off"))
        assertEquals("""{"grandTichuBonus": 200}""", rawSettings("t-on"))
        assertEquals("""{"grandTichuBonus": 200}""", rawSettings("t-unset"))
        // A row that already carries a bonus is left untouched.
        assertEquals("""{"allowGrandTichu": false, "grandTichuBonus": 200}""", rawSettings("t-done"))
    }

    @Test
    fun `V18 gives tournaments without total rounds the default 4 and keeps a stored value`() {
        migrateTo("17")
        insertTournament("t-old", """{"grandTichuBonus": 100}""")
        insertTournament("t-set", """{"grandTichuBonus": 200, "totalRounds": 6}""")

        migrateTo("18")

        // Raw JSON is asserted: reading through the adapter would hide a missing key behind the domain default.
        assertEquals("""{"totalRounds": 4, "grandTichuBonus": 100}""", rawSettings("t-old"))
        assertEquals("""{"totalRounds": 6, "grandTichuBonus": 200}""", rawSettings("t-set"))
    }

    @Test
    fun `V19 gives tournaments without a double win bonus the default 200 and keeps a stored value`() {
        migrateTo("18")
        insertTournament("t-old", """{"grandTichuBonus": 100, "totalRounds": 4}""")
        insertTournament("t-set", """{"grandTichuBonus": 200, "totalRounds": 6, "doubleWinBonus": 100}""")

        migrateTo("19")

        // Raw JSON is asserted: reading through the adapter would hide a missing key behind the domain default.
        assertEquals("""{"totalRounds": 4, "doubleWinBonus": 200, "grandTichuBonus": 100}""", rawSettings("t-old"))
        assertEquals("""{"totalRounds": 6, "doubleWinBonus": 100, "grandTichuBonus": 200}""", rawSettings("t-set"))
    }

    @Test
    fun `V24 leaves existing teams not withdrawn`() {
        migrateTo("23")
        insertTournament("t-running", "{}")
        jdbc.update("INSERT INTO teams (id, tournament_id, name) VALUES ('team-legacy', 't-running', 'Legacy')")

        migrateTo("24")

        val row = jdbc.queryForMap(
            "SELECT withdrawal_reason, forfeit_compensation, withdrawal_round, withdrawn_at, withdrawn_by " +
                "FROM teams WHERE id = 'team-legacy'",
        )
        assertEquals(List(5) { null }, row.values.toList())
    }

    @Test
    fun `V24 accepts a complete withdrawal and rejects a partial or unknown one`() {
        migrateTo("24")
        insertTournament("t-running", "{}")

        insertWithdrawnTeam("team-ok", reason = "DISQUALIFIED", compensation = "HALF", round = 2)
        assertEquals(
            "DISQUALIFIED",
            jdbc.queryForObject("SELECT withdrawal_reason FROM teams WHERE id = 'team-ok'", String::class.java),
        )

        // The all-or-none check: a reason without the rest of the withdrawal is refused.
        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO teams (id, tournament_id, name, withdrawal_reason) " +
                    "VALUES ('team-partial', 't-running', 'Partial', 'VOLUNTARY')",
            )
        }
        assertFailsWith<DataIntegrityViolationException> {
            insertWithdrawnTeam("team-reason", reason = "BANNED", compensation = "FULL", round = 1)
        }
        assertFailsWith<DataIntegrityViolationException> {
            insertWithdrawnTeam("team-compensation", reason = "VOLUNTARY", compensation = "DOUBLE", round = 1)
        }
    }

    /** Inserts a withdrawn team of tournament `t-running` with every V24 column set. */
    private fun insertWithdrawnTeam(id: String, reason: String, compensation: String, round: Int) {
        jdbc.update(
            "INSERT INTO teams (id, tournament_id, name, withdrawal_reason, forfeit_compensation, withdrawal_round, " +
                "withdrawn_at, withdrawn_by) VALUES (?, 't-running', ?, ?, ?, ?, now(), ?)",
            id,
            id,
            reason,
            compensation,
            round,
            OWNER_ID,
        )
    }

    /** Runs Flyway against this test's schema up to [version] (inclusive). Callable repeatedly. */
    private fun migrateTo(version: String) {
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .target(version)
            .load()
            .migrate()
    }

    /** Inserts a tournament with the given raw settings JSON, creating its owner on first use. */
    private fun insertTournament(id: String, settingsJson: String) {
        insertAdminRow(jdbc, OWNER_ID, "owner@example.com")
        jdbc.update(
            "INSERT INTO tournaments (id, owner_id, name, description, max_teams, settings, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Legacy', '', 16, ?::jsonb, 'SETUP', now(), now())",
            id,
            OWNER_ID,
            settingsJson,
        )
    }

    private fun rawSettings(id: String): String? =
        jdbc.queryForObject("SELECT settings::text FROM tournaments WHERE id = ?", String::class.java, id)

    private companion object {
        const val OWNER_ID = "admin-owner"

        /** Appends `currentSchema=<schema>` to a JDBC URL, respecting an existing query string. */
        fun withCurrentSchema(jdbcUrl: String, schema: String): String {
            val separator = if (jdbcUrl.contains('?')) '&' else '?'
            return "$jdbcUrl${separator}currentSchema=$schema"
        }
    }
}
