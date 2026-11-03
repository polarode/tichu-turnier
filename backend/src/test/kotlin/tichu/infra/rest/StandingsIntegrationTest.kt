package tichu.infra.rest

import tichu.domain.AdminUserId
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
import tichu.domain.SessionTokens
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamResult
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Integration test for the admin standings read and the Total Rounds change (S7a,
 * #114), over MockMvc and the real security chain against Testcontainers
 * PostgreSQL. The standings scenario is the one in the shared
 * `contract-fixtures/standings.json`, which the frontend renders too: the API
 * must answer exactly that file's `response`.
 */
@SpringBootTest(classes = [Application::class])
class StandingsIntegrationTest(
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

    private val tournamentId = TournamentId("t-standings")
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

    /** The scenario described in `contract-fixtures/standings.json`. */
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
        (1..4).forEach { game(2, 1, it, "falcons", "ravens", 90) }
        confirm(2, 1, "falcons")
    }

    private fun standings(cookie: Cookie = ownerCookie): ResultActions =
        mockMvc.perform(get("/api/tournaments/${tournamentId.value}/standings").cookie(cookie))

    private fun setTotalRounds(totalRounds: Int): ResultActions = mockMvc.perform(
        put("/api/tournaments/${tournamentId.value}/total-rounds")
            .cookie(ownerCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"totalRounds":$totalRounds}"""),
    )

    @Test
    fun `the standings answer exactly the shared fixture's response`() {
        seedFixtureScenario()

        val body = standings().andExpect(status().isOk).andReturn().response.contentAsString

        val fixture = objectMapper.readTree(File("../contract-fixtures/standings.json"))
        assertEquals(fixture.get("response"), objectMapper.readTree(body))
    }

    @Test
    fun `a tournament still in setup answers TOURNAMENT_NOT_STARTED`() {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.SETUP))

        standings()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_STARTED"))
    }

    @Test
    fun `another admin cannot read the standings`() {
        val intruder = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        standings(intruder).andExpect(status().isNotFound)
    }

    @Test
    fun `a team session cannot read the standings`() {
        // The team filter only authenticates inside /api/team/, so here it is unauthenticated (401).
        standings(teamSession("team-falcons")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `total rounds change while active and show in the tournament read`() {
        round(1, listOf("falcons", "owls"), listOf("ravens", "badgers", "wolves"))

        setTotalRounds(6)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settings.totalRounds").value(6))
        mockMvc.perform(get("/api/tournaments/${tournamentId.value}").cookie(ownerCookie))
            .andExpect(jsonPath("$.settings.totalRounds").value(6))
    }

    @Test
    fun `total rounds below the created rounds answer a field-anchored 409`() {
        round(1, listOf("falcons", "owls"), listOf("ravens", "badgers", "wolves"))
        round(2, listOf("falcons", "ravens"), listOf("owls", "badgers", "wolves"))

        setTotalRounds(1)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOTAL_ROUNDS_BELOW_ROUND_COUNT"))
            .andExpect(jsonPath("$.field").value("totalRounds"))
    }

    @Test
    fun `total rounds outside 1 to 20 answer a field-anchored 400`() {
        setTotalRounds(21)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.field").value("totalRounds"))
    }

    @Test
    fun `create stores the total rounds from the request body, defaulting to 4`() {
        fun create(settings: String) = mockMvc.perform(
            post("/api/tournaments")
                .cookie(ownerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Autumn Open","maxTeams":8,"settings":$settings}"""),
        ).andExpect(status().isCreated)

        create("""{"totalRounds":7}""").andExpect(jsonPath("$.settings.totalRounds").value(7))
        create("{}").andExpect(jsonPath("$.settings.totalRounds").value(4))
    }

    private fun teamSession(teamId: String): Cookie {
        val tokens = context.getBean(TeamTokenRepository::class.java)
        val sessionTokens = context.getBean(SessionTokens::class.java)
        val raw = sessionTokens.generate()
        tokens.save(TeamToken(TeamId(teamId), sessionTokens.hash(raw), revealed = true, createdAt = now, updatedAt = now))
        val result = mockMvc.perform(
            post("/api/team/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE) ?: error("exchange did not set a session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }
}
