package tichu.infra.rest

import tichu.domain.AdminUserRepository
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.GameRepository
import tichu.domain.Pairing
import tichu.domain.PasswordHasher
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamResult
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.seedAdmin
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Integration test for the anonymous public read of the tournament statistics (S12, #121),
 * over MockMvc and the real security chain against Testcontainers PostgreSQL. The scenario
 * is one completed table: Falcons and Owls, four games where Falcons' first player calls and
 * makes a grand Tichu every time. Expected values are worked by hand from that scenario.
 * A non-public tournament answers not found, a public one answers RESULTS_NOT_PUBLISHED
 * until it is PUBLISHED.
 */
@SpringBootTest(classes = [Application::class])
class PublicStatisticsIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = TournamentId("t-public-statistics")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        val owner = seedAdmin(adminUsers, passwordHasher, "owner-${UUID.randomUUID()}@example.com")
        tournamentRepository.save(
            Tournament(
                tournamentId, owner.id, "Spring Classic", "", 16,
                TournamentSettings(totalRounds = 1), TournamentStatus.ACTIVE, now, now,
                publicVisibility = true,
            ),
        )
        listOf("falcons" to "Falcons", "owls" to "Owls", "ravens" to "Ravens", "badgers" to "Badgers")
            .forEach { (id, name) -> teamRepository.save(team("team-$id", name)) }
        seedCompletedTable()
    }

    private fun team(id: String, name: String) = Team(
        TeamId(id),
        tournamentId,
        name,
        listOf(
            Player(PlayerId("$id-1"), tournamentId, TeamId(id), "$name one"),
            Player(PlayerId("$id-2"), tournamentId, TeamId(id), "$name two"),
        ),
    )

    /** One completed table, Falcons against Owls, in round 1 (the only round of the tournament). */
    private fun seedCompletedTable() {
        roundRepository.save(
            Round(
                RoundId("r-1"),
                tournamentId,
                1,
                Pairing(listOf(Table(TableId(1), listOf(TeamId("team-falcons"), TeamId("team-owls"))))),
            ),
        )
        (1..4).forEach { number ->
            // Falcons' first player calls a grand Tichu and finishes first; Falcons win 60 to 40.
            transactionTemplate.execute {
                gameRepository.create(
                    Game.record(
                        GameKey(tournamentId, 1, 1, number),
                        listOf(
                            TeamResult(TeamId("team-falcons"), 60, listOf(
                                PlayerResult(PlayerId("team-falcons-1"), 1, TichuCall.GRAND, 0),
                                PlayerResult(PlayerId("team-falcons-2"), 3, TichuCall.NONE, 0),
                            )),
                            TeamResult(TeamId("team-owls"), 40, listOf(
                                PlayerResult(PlayerId("team-owls-1"), 2, TichuCall.NONE, 0),
                                PlayerResult(PlayerId("team-owls-2"), 4, TichuCall.NONE, 0),
                            )),
                        ),
                        beschiss = false,
                        settings = TournamentSettings(),
                    ),
                )
            }
        }
        listOf("team-falcons", "team-owls").forEach {
            confirmationRepository.add(TableKey(tournamentId, 1, 1), TeamId(it), now)
        }
    }

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))
    }

    /** The anonymous public read: no cookie, no session. */
    private fun publicStatistics() = mockMvc.perform(get("/api/public/tournaments/${tournamentId.value}/statistics"))

    @Test
    fun `a non-public tournament is not found, even once published`() {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.PUBLISHED, publicVisibility = false))

        publicStatistics()
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a public tournament whose results are not published answers results not published`() {
        publicStatistics()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"))

        setStatus(TournamentStatus.COMPLETED)
        publicStatistics()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"))
    }

    @Test
    fun `a published public tournament answers the statistics with the hand-worked values`() {
        setStatus(TournamentStatus.PUBLISHED)

        val body: JsonNode = objectMapper.readTree(
            publicStatistics().andExpect(status().isOk).andReturn().response.contentAsString,
        )

        fun teamNode(id: String) = body["teams"].single { it["teamId"].asText() == id }
        val falcons = teamNode("team-falcons")
        assertEquals("Falcons", falcons["teamName"].asText())
        assertEquals(0, falcons["successfulSmallTichu"].asInt())
        assertEquals(4, falcons["successfulGrandTichu"].asInt())
        assertEquals(0, falcons["bombCount"].asInt())
        assertEquals(0.0, falcons["bombsPerGame"].asDouble())
        assertEquals(0, falcons["doubleWins"].asInt())
        val owls = teamNode("team-owls")
        assertEquals("Owls", owls["teamName"].asText())
        assertEquals(0, owls["successfulGrandTichu"].asInt())
        assertEquals(0.0, owls["bombsPerGame"].asDouble())

        assertEquals(
            objectMapper.readTree("""[{"metric":"SUCCESSFUL_TICHU","value":4,"teamIds":["team-falcons"]}]"""),
            body["highlights"],
        )
        // Four game wins at 2 VP each: 8 VP for Falcons, and nothing for the losing Owls.
        fun points(id: String): String = body["trends"].single { it["teamId"].asText() == id }["points"].toString()
        assertEquals("[{\"roundNumber\":1,\"victoryPoints\":8}]", points("team-falcons"))
        assertEquals("[{\"roundNumber\":1,\"victoryPoints\":0}]", points("team-owls"))
    }
}
