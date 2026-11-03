package tichu.infra.rest

import tichu.application.api.StartNextRound
import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.JpaRoundRepository
import tichu.infra.persistence.SpringDataRoundRepository
import tichu.infra.persistence.SpringDataRoundTableRepository
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.insertAdminRow
import tichu.testsupport.teamOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the transactional contract of starting the next round against real
 * PostgreSQL (S7b, #115): a failure after the new round's rows were written must
 * leave no trace of it. The failure is injected by a `@Primary` round repository
 * that saves through the real adapter, then throws.
 */
@SpringBootTest(classes = [Application::class, NextRoundRollbackIntegrationTest.FailAfterRoundSave::class])
class NextRoundRollbackIntegrationTest(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val startNextRound: StartNextRound,
) : PostgresBackedIntegrationTest() {

    @TestConfiguration
    class FailAfterRoundSave {
        @Bean
        @Primary
        fun failAfterRoundSave(
            springDataRoundRepository: SpringDataRoundRepository,
            springDataRoundTableRepository: SpringDataRoundTableRepository,
        ): RoundRepository {
            val real = JpaRoundRepository(springDataRoundRepository, springDataRoundTableRepository)
            return object : RoundRepository by real {
                override fun save(round: Round): Round {
                    real.save(round)
                    if (round.number > 1) throw IllegalStateException("injected failure after the round save")
                    return round
                }
            }
        }
    }

    private val tournamentId = TournamentId("t-rollback")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        insertAdminRow(jdbcTemplate, "admin-owner", "owner@example.com")
        tournamentRepository.save(
            Tournament(tournamentId, AdminUserId("admin-owner"), "Spring Classic", "", 16, TournamentSettings(), TournamentStatus.ACTIVE, now, now),
        )
        listOf("a", "b").forEach { teamRepository.save(teamOf(tournamentId, it)) }
        roundRepository.save(Round(RoundId("r-1"), tournamentId, 1, Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b")))))))
    }

    @Test
    fun `a failure after the round save leaves no new round or table rows`() {
        // Confirmed by both teams: the table counts as completed even without games
        // (games only feed the standings), so the advance reaches the save.
        listOf("a", "b").forEach { confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId(it), now) }

        assertFailsWith<IllegalStateException> { startNextRound.start(tournamentId, AdminUserId("admin-owner")) }

        val rounds = jdbcTemplate.queryForObject("SELECT count(*) FROM rounds", Int::class.java)!!
        val tables = jdbcTemplate.queryForObject("SELECT count(*) FROM round_tables", Int::class.java)!!
        assertEquals(1, rounds, "round 2 rolled back")
        assertEquals(1, tables, "its table rows rolled back")
    }
}
