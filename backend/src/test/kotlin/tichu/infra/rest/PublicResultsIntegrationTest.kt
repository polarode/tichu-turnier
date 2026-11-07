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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration test for the anonymous public read of a Published tournament (S9c, #127),
 * over MockMvc and the real security chain against Testcontainers PostgreSQL. The
 * scenario is the one in the shared `contract-fixtures/public-tournament-published.json`,
 * which the frontend renders too: the API must answer exactly that file's `response`.
 * Table Totals and the Final Ranking appear only once Published; the payload carries no
 * per-game data in any status.
 */
@SpringBootTest(classes = [Application::class])
class PublicResultsIntegrationTest(
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

    private val tournamentId = TournamentId("t-harvest-cup")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        val owner = seedAdmin(adminUsers, passwordHasher, "owner-${UUID.randomUUID()}@example.com")
        tournamentRepository.save(
            Tournament(
                tournamentId, owner.id, "Harvest Cup", "", 16, TournamentSettings(), TournamentStatus.PUBLISHED, now, now,
                publicVisibility = true,
            ),
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

    private fun round(number: Int, vararg tables: List<String>) = roundRepository.save(
        Round(
            RoundId("r-$number"),
            tournamentId,
            number,
            Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map { TeamId("team-$it") }) }),
        ),
    )

    /** A game decided by base score alone: [first] takes [firstBase] of the 100, finishing first when it wins. */
    private fun game(round: Int, table: Int, number: Int, first: String, second: String, firstBase: Int) {
        val firstWins = firstBase >= 50
        fun players(team: String, wins: Boolean) = listOf(
            PlayerResult(PlayerId("team-$team-1"), if (wins) 1 else 2, TichuCall.NONE, 0),
            PlayerResult(PlayerId("team-$team-2"), if (wins) 3 else 4, TichuCall.NONE, 0),
        )
        // The adapter's create requires the caller's transaction, as a use case provides it.
        transactionTemplate.execute {
            gameRepository.create(
                Game.record(
                    GameKey(tournamentId, round, table, number),
                    listOf(
                        TeamResult(TeamId("team-$first"), firstBase, players(first, firstWins)),
                        TeamResult(TeamId("team-$second"), 100 - firstBase, players(second, !firstWins)),
                    ),
                    beschiss = false,
                    settings = TournamentSettings(),
                ),
            )
        }
    }

    private fun confirm(round: Int, table: Int, vararg teams: String) = teams.forEach {
        confirmationRepository.add(TableKey(tournamentId, round, table), TeamId("team-$it"), now)
    }

    /** The scenario described in `contract-fixtures/public-tournament-published.json`. */
    private fun seedFixtureScenario() {
        round(1, listOf("falcons", "owls"), listOf("ravens", "badgers", "wolves"))
        (1..4).forEach { game(1, 1, it, "falcons", "owls", 50) }
        // Trio schedule by seat (ravens, badgers, wolves): 1 RB, 2 RW, 3 BW, 4 RB, 5 RW, 6 BW.
        listOf("ravens" to "badgers", "ravens" to "wolves", "badgers" to "wolves").let { matchups ->
            (1..6).forEach { number ->
                val (first, second) = matchups[(number - 1) % 3]
                game(1, 2, number, first, second, 70)
            }
        }
        confirm(1, 1, "falcons", "owls")
        confirm(1, 2, "ravens", "badgers", "wolves")

        round(2, listOf("falcons", "ravens"), listOf("owls", "badgers", "wolves"))
        (1..4).forEach { game(2, 1, it, "falcons", "ravens", 60) }
        confirm(2, 1, "falcons", "ravens")
        // Trio schedule by seat (owls, badgers, wolves): 1 OB, 2 OW, 3 BW, 4 OB, 5 OW, 6 BW.
        listOf("owls" to "badgers", "owls" to "wolves", "badgers" to "wolves").let { matchups ->
            (1..6).forEach { number ->
                val (first, second) = matchups[(number - 1) % 3]
                game(2, 2, number, first, second, 50)
            }
        }
        confirm(2, 2, "owls", "badgers", "wolves")
    }

    /** The anonymous public read: no cookie, no session. */
    private fun publicTournament(): JsonNode {
        val body = mockMvc.perform(get("/api/public/tournaments/${tournamentId.value}"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(body)
    }

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun assertKeys(node: JsonNode, expected: Set<String>, label: String) {
        assertEquals(expected, node.propertyNames().toSet(), "$label: $node")
    }

    @Test
    fun `a published tournament answers exactly the shared fixture's response`() {
        seedFixtureScenario()

        val body = publicTournament()

        val fixture = objectMapper.readTree(File("../contract-fixtures/public-tournament-published.json"))
        assertEquals(fixture.get("response"), body)
    }

    @Test
    fun `a published tournament carries no per-game data`() {
        seedFixtureScenario()

        val body = publicTournament()

        assertKeys(body, setOf("id", "name", "status", "rounds", "finalRanking"), "top level")
        body["rounds"].forEach { round ->
            assertKeys(round, setOf("number", "tables"), "round")
            round["tables"].forEach { table ->
                assertKeys(table, setOf("tableNumber", "trio", "teams"), "table")
                table["teams"].forEach { team ->
                    assertKeys(team, setOf("id", "name", "players", "totals"), "team")
                    team["players"].forEach { assertKeys(it, setOf("id", "name"), "player") }
                    assertKeys(team["totals"], setOf("victoryPoints", "tichuPoints"), "totals")
                }
            }
        }
        body["finalRanking"].forEach {
            assertKeys(it, setOf("rank", "teamId", "teamName", "gamesPlayed", "victoryPoints", "tichuPoints"), "ranking entry")
        }
        val raw = body.toString().lowercase()
        listOf(
            "score", "result", "confirm", "beschiss", "bomb", "call", "position",
            "owner", "token", "credential", "password", "link", "session",
        ).forEach { fragment ->
            assertFalse(raw.contains(fragment), "published read must not mention '$fragment': $body")
        }
    }

    @Test
    fun `active and completed tournaments carry no totals or final ranking`() {
        seedFixtureScenario()

        listOf(TournamentStatus.ACTIVE, TournamentStatus.COMPLETED).forEach { status ->
            tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))

            val body = publicTournament()

            assertEquals(status.name, body["status"].asText())
            assertKeys(body, setOf("id", "name", "status", "rounds"), "$status top level")
            val teams = body["rounds"].flatMap { round -> round["tables"].flatMap { it["teams"].items() } }
            assertEquals(10, teams.size, "$status: the pairings are still listed")
            teams.forEach { team ->
                assertFalse(team.has("totals"), "$status must not carry totals: $team")
            }
        }
    }
}
