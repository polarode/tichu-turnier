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
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.loginAsSeededAdmin
import tichu.testsupport.seedAdmin
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Integration test for the admin statistics read (S12, #121), over MockMvc and the real
 * security chain against Testcontainers PostgreSQL. The scenario is one completed
 * table: Falcons and Owls, four games where Falcons' first player calls and makes a
 * grand Tichu every time. Expected values are worked by hand from that scenario.
 */
@SpringBootTest(classes = [Application::class])
class StatisticsIntegrationTest(
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
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = TournamentId("t-statistics")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private lateinit var ownerCookie: Cookie

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        val email = "owner-${UUID.randomUUID()}@example.com"
        val owner = seedAdmin(adminUsers, passwordHasher, email)
        ownerCookie = mockMvc.loginAndCaptureSession(objectMapper, email)
        tournamentRepository.save(
            Tournament(tournamentId, owner.id, "Spring Classic", "", 16, TournamentSettings(), TournamentStatus.ACTIVE, now, now),
        )
        listOf("falcons" to "Falcons", "owls" to "Owls", "ravens" to "Ravens", "badgers" to "Badgers", "wolves" to "Wolves")
            .forEach { (id, name) -> teamRepository.save(team("team-$id", name)) }
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

    /** One completed table, Falcons against Owls, in round 1. */
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

    private fun round(number: Int, vararg tables: List<String>) = roundRepository.save(
        Round(
            RoundId("r-$number"),
            tournamentId,
            number,
            Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map { TeamId("team-$it") }) }),
        ),
    )

    /** One team's two players as the finish position (null after a double win loss), call and bombs. */
    private data class Seat(val position: Int?, val call: TichuCall = TichuCall.NONE, val bombs: Int = 0)

    /** One team's side of a game: its base score and its two seats, first player then second. */
    private data class Side(val team: String, val base: Int, val first: Seat, val second: Seat)

    private fun game(round: Int, table: Int, number: Int, a: Side, b: Side, beschiss: Boolean = false) {
        fun players(side: Side) = listOf(
            PlayerResult(PlayerId("team-${side.team}-1"), side.first.position, side.first.call, side.first.bombs),
            PlayerResult(PlayerId("team-${side.team}-2"), side.second.position, side.second.call, side.second.bombs),
        )
        transactionTemplate.execute {
            gameRepository.create(
                Game.record(
                    GameKey(tournamentId, round, table, number),
                    listOf(
                        TeamResult(TeamId("team-${a.team}"), a.base, players(a)),
                        TeamResult(TeamId("team-${b.team}"), b.base, players(b)),
                    ),
                    beschiss = beschiss,
                    settings = TournamentSettings(),
                ),
            )
        }
    }

    /** The scenario described in `contract-fixtures/statistics.json`, all tables completed. */
    private fun seedFixtureScenario() {
        round(1, listOf("falcons", "owls"), listOf("ravens", "badgers"))
        game(1, 1, 1, Side("falcons", 60, Seat(1, TichuCall.GRAND), Seat(3, bombs = 1)), Side("owls", 40, Seat(2), Seat(4)))
        game(1, 1, 2, Side("owls", 70, Seat(1, TichuCall.SMALL), Seat(3, bombs = 2)), Side("falcons", 30, Seat(2), Seat(4)))
        game(1, 1, 3, Side("falcons", 50, Seat(1), Seat(3)), Side("owls", 50, Seat(2), Seat(4)))
        game(1, 1, 4, Side("falcons", 0, Seat(1, TichuCall.SMALL), Seat(2)), Side("owls", 0, Seat(null), Seat(null)))
        game(1, 2, 1, Side("ravens", 50, Seat(1), Seat(3)), Side("badgers", 50, Seat(2, bombs = 1), Seat(4)))
        game(1, 2, 2, Side("ravens", 70, Seat(1, TichuCall.GRAND), Seat(3, bombs = 3)), Side("badgers", 30, Seat(2), Seat(4)))
        game(1, 2, 3, Side("ravens", 40, Seat(2), Seat(4)), Side("badgers", 60, Seat(3), Seat(1, TichuCall.SMALL)), beschiss = true)
        game(1, 2, 4, Side("ravens", 50, Seat(1), Seat(3)), Side("badgers", 50, Seat(2), Seat(4)))
        confirmTable(1, 1, "falcons", "owls")
        confirmTable(1, 2, "ravens", "badgers")

        round(2, listOf("falcons", "ravens"), listOf("owls", "badgers"))
        game(2, 1, 1, Side("falcons", 50, Seat(1), Seat(3)), Side("ravens", 50, Seat(2), Seat(4)))
        game(2, 1, 2, Side("falcons", 20, Seat(2), Seat(4)), Side("ravens", 80, Seat(1, TichuCall.SMALL), Seat(3, bombs = 1)))
        game(2, 1, 3, Side("falcons", 90, Seat(1), Seat(3, bombs = 1)), Side("ravens", 10, Seat(2), Seat(4)), beschiss = true)
        game(2, 1, 4, Side("falcons", 40, Seat(2, bombs = 2), Seat(4)), Side("ravens", 60, Seat(1), Seat(3)))
        game(2, 2, 1, Side("owls", 70, Seat(1, TichuCall.GRAND), Seat(3)), Side("badgers", 30, Seat(2), Seat(4)))
        game(2, 2, 2, Side("owls", 0, Seat(null, TichuCall.SMALL), Seat(null)), Side("badgers", 0, Seat(1, bombs = 2), Seat(2)))
        game(2, 2, 3, Side("owls", 60, Seat(1, bombs = 1), Seat(3)), Side("badgers", 40, Seat(2), Seat(4)))
        game(2, 2, 4, Side("owls", 45, Seat(2), Seat(4)), Side("badgers", 55, Seat(1, TichuCall.SMALL), Seat(3)))
        confirmTable(2, 1, "falcons", "ravens")
        confirmTable(2, 2, "owls", "badgers")
    }

    private fun confirmTable(round: Int, table: Int, vararg teams: String) = teams.forEach {
        confirmationRepository.add(TableKey(tournamentId, round, table), TeamId("team-$it"), now)
    }

    private fun statistics(cookie: Cookie = ownerCookie): ResultActions =
        mockMvc.perform(get("/api/tournaments/${tournamentId.value}/statistics").cookie(cookie))

    @Test
    fun `the statistics answer exactly the shared fixture's response`() {
        seedFixtureScenario()

        val body = statistics().andExpect(status().isOk).andReturn().response.contentAsString

        val fixture = objectMapper.readTree(File("../contract-fixtures/statistics.json"))
        assertEquals(fixture.get("response"), objectMapper.readTree(body))
    }

    @Test
    fun `the statistics of one completed table are answered with the hand-worked values`() {
        seedCompletedTable()

        val body = objectMapper.readTree(
            statistics().andExpect(status().isOk).andReturn().response.contentAsString,
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
        // A team never seated stays at zero, with no bombs per game to report.
        assertEquals(true, teamNode("team-ravens")["bombsPerGame"].isNull)

        assertEquals(
            objectMapper.readTree("""[{"metric":"SUCCESSFUL_TICHU","value":4.0,"teamIds":["team-falcons"]},{"metric":"TICHU_CALLS","value":4.0,"teamIds":["team-falcons"]},{"metric":"GOOD_HORSE","value":130.0,"teamIds":["team-falcons"]}]"""),
            body["highlights"],
        )
        fun points(id: String): List<Pair<Int, Int>> {
            val trend: JsonNode = body["trends"].single { it["teamId"].asText() == id }
            val pairs = mutableListOf<Pair<Int, Int>>()
            for (point in trend["points"]) {
                pairs += Pair(point["roundNumber"].asInt(), point["victoryPoints"].asInt())
            }
            return pairs
        }
        // Four game wins at 2 VP each: 8 VP for Falcons, and nothing for the losing Owls.
        assertEquals(listOf(1 to 8), points("team-falcons"))
        assertEquals(listOf(1 to 0), points("team-owls"))
    }

    @Test
    fun `a tournament still in setup answers TOURNAMENT_NOT_STARTED`() {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.SETUP))

        statistics()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_STARTED"))
    }

    @Test
    fun `another admin cannot read the statistics`() {
        val intruder = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        statistics(intruder).andExpect(status().isNotFound)
    }
}
