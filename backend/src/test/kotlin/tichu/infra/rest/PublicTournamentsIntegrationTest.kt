package tichu.infra.rest

import tichu.domain.AdminUserRepository
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.GameRepository
import tichu.domain.PasswordHasher
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamResult
import tichu.domain.TichuCall
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.loginAsSeededAdmin
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration test for the anonymous public read `GET /api/public/tournaments` (ADR 0008).
 * Drives the real security chain: the public list is reachable without any session, only
 * public non-SETUP tournaments appear with their team counts, and the rest of the api prefix
 * stays closed to anonymous callers (the admin catch-all under the api prefix).
 */
@SpringBootTest(classes = [tichu.main.Application::class])
class PublicTournamentsIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val tableConfirmationRepository: TableConfirmationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var sessionCookie: Cookie

    @BeforeEach
    fun authenticate() {
        jdbcTemplate.update("DELETE FROM tournaments")
        sessionCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
    }

    @Test
    fun `anonymous caller can read the public tournament list`() {
        mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `lists only public tournaments that are active or published, with their team counts`() {
        val publicActive = createTournament("Spring Open")
        setPublicVisibility(publicActive, true)
        setStatus(publicActive, "ACTIVE")
        createTeam(publicActive, "Alpha")
        createTeam(publicActive, "Bravo")

        val publicPublished = createTournament("Winter Cup")
        setPublicVisibility(publicPublished, true)
        setStatus(publicPublished, "PUBLISHED")
        createTeam(publicPublished, "Charlie")

        val publicSetup = createTournament("Draft Cup")
        setPublicVisibility(publicSetup, true)
        createTeam(publicSetup, "Delta")

        val privateActive = createTournament("Secret Cup")
        setStatus(privateActive, "ACTIVE")
        createTeam(privateActive, "Echo")

        val response = mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val items = objectMapper.readTree(response)

        assertEquals(2, items.size(), "only public ACTIVE and PUBLISHED tournaments are listed: $response")
        val byId = items.associateBy({ it["id"].asText() }, { it })
        assertEquals(setOf(publicActive, publicPublished), byId.keys)
        assertEquals("ACTIVE", byId.getValue(publicActive)["status"].asText())
        assertEquals(2, byId.getValue(publicActive)["teamCount"].asInt())
        assertEquals("PUBLISHED", byId.getValue(publicPublished)["status"].asText())
        assertEquals(1, byId.getValue(publicPublished)["teamCount"].asInt())
    }

    @Test
    fun `public list exposes only public facts and no owner or credential fields`() {
        val id = createTournament("Facts Only")
        setPublicVisibility(id, true)
        setStatus(id, "ACTIVE")
        createTeam(id, "Foxtrot")

        val response = mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val items = objectMapper.readTree(response)

        val allowedKeys = setOf("id", "name", "status", "teamCount")
        items.forEach { item ->
            assertEquals(allowedKeys, item.propertyNames().toSet(), "unexpected fields in $item")
        }
        val forbiddenFragments = listOf("owner", "token", "credential", "password", "accesslink", "link", "session")
        forbiddenFragments.forEach { fragment ->
            assertFalse(response.lowercase().contains(fragment), "public body must not mention '$fragment': $response")
        }
    }

    @Test
    fun `public pairings list every table with the trio last, teams in seat order and their players`() {
        val id = createPublicTournament("Odd Cup")
        val created = (1..5).map { createTeam(id, "Team $it") }
        val started = startTournament(id)

        val body = getPublicPairings(id)
        assertPairingsShape(body)
        assertEquals(id, body["id"].asText())
        assertEquals("Odd Cup", body["name"].asText())
        assertEquals("ACTIVE", body["status"].asText())

        val rounds = body["rounds"]
        assertEquals(listOf(1), rounds.items().map { it["number"].asInt() })
        val tables = rounds[0]["tables"]
        assertEquals(listOf(1, 2), tables.items().map { it["tableNumber"].asInt() })
        assertEquals(listOf(false, true), tables.items().map { it["trio"].asBoolean() }, "the last table is the trio")
        assertEquals(listOf(2, 3), tables.items().map { it["teams"].size() })

        val seatedInStartOrder = started["round"]["tables"]
        tables.forEachIndexed { index, table ->
            assertEquals(
                seatedInStartOrder[index]["teamIds"].items().map { it.asText() },
                table["teams"].items().map { it["id"].asText() },
                "teams are listed in seat order of table ${index + 1}",
            )
        }

        val createdById = created.associateBy { it["id"].asText() }
        tables.forEach { table ->
            table["teams"].forEach { team ->
                val expected = createdById.getValue(team["id"].asText())
                assertEquals(expected["name"].asText(), team["name"].asText())
                assertEquals(
                    expected["players"].items().map { it["name"].asText() },
                    team["players"].items().map { it["name"].asText() },
                    "players of ${team["name"].asText()}",
                )
            }
        }
    }

    @Test
    fun `before publication the pairings carry no games, results, table status or confirmations`() {
        val id = createPublicTournament("Quiet Cup")
        val created = (1..5).map { createTeam(id, "Team $it") }
        val started = startTournament(id)
        val firstTable = started["round"]["tables"][0]["teamIds"].items().map { TeamId(it.asText()) }
        val createdById = created.associateBy { it["id"].asText() }
        fun playerIds(team: TeamId): List<PlayerId> =
            createdById.getValue(team.value)["players"].items().map { PlayerId(it["id"].asText()) }
        fun seat(team: TeamId, base: Int, positions: Pair<Int, Int>) = TeamResult(
            teamId = team,
            baseScore = base,
            players = listOf(
                PlayerResult(playerIds(team)[0], positions.first, TichuCall.NONE, 0),
                PlayerResult(playerIds(team)[1], positions.second, TichuCall.NONE, 0),
            ),
        )
        transactionTemplate.executeWithoutResult {
            gameRepository.create(
                Game.record(
                    key = GameKey(TournamentId(id), 1, 1, 1),
                    teams = listOf(seat(firstTable[0], 60, 1 to 3), seat(firstTable[1], 40, 2 to 4)),
                    beschiss = false,
                    settings = TournamentSettings(),
                ),
            )
            tableConfirmationRepository.add(TableKey(TournamentId(id), 1, 1), firstTable[0], Instant.now())
        }

        val active = getPublicPairings(id)
        assertPairingsShape(active)
        assertNoResultFields(active)

        setStatus(id, "COMPLETED")
        val completed = getPublicPairings(id)
        assertEquals("COMPLETED", completed["status"].asText())
        assertPairingsShape(completed)
        assertNoResultFields(completed)
    }

    @Test
    fun `a non-public, a setup or an unknown tournament is not found for anonymous callers`() {
        val nonPublicActive = createTournament("Hidden Cup")
        setStatus(nonPublicActive, "ACTIVE")
        val publicSetup = createPublicTournament("Draft Cup")

        listOf(nonPublicActive, publicSetup, "does-not-exist").forEach { id ->
            mockMvc.perform(get("/api/public/tournaments/$id"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        }
    }

    @Test
    fun `admin list and team endpoints stay closed to anonymous callers`() {
        mockMvc.perform(get("/api/tournaments"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(get("/api/team/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `the public permit is GET only, so writes under the public prefix stay closed`() {
        mockMvc.perform(
            post("/api/public/tournaments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sneaky","maxTeams":16}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun createPublicTournament(name: String): String {
        val id = createTournament(name)
        setPublicVisibility(id, true)
        return id
    }

    private fun startTournament(id: String): JsonNode =
        objectMapper.readTree(
            mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString,
        )

    private fun getPublicPairings(id: String): JsonNode =
        objectMapper.readTree(
            mockMvc.perform(get("/api/public/tournaments/$id"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString,
        )

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

    /** Every level of the public pairings carries exactly the documented keys, nothing more. */
    private fun assertPairingsShape(body: JsonNode) {
        assertEquals(setOf("id", "name", "status", "rounds"), body.propertyNames().toSet(), "top level: $body")
        body["rounds"].forEach { round ->
            assertEquals(setOf("number", "tables"), round.propertyNames().toSet(), "round: $round")
            round["tables"].forEach { table ->
                assertEquals(setOf("tableNumber", "trio", "teams"), table.propertyNames().toSet(), "table: $table")
                table["teams"].forEach { team ->
                    assertEquals(setOf("id", "name", "players"), team.propertyNames().toSet(), "team: $team")
                    team["players"].forEach { player ->
                        assertEquals(setOf("id", "name"), player.propertyNames().toSet(), "player: $player")
                    }
                }
            }
        }
    }

    /** No game, result, score, confirmation or table-status detail may leak before publication. */
    private fun assertNoResultFields(body: JsonNode) {
        val raw = body.toString().lowercase()
        val resultFragments = listOf(
            "game", "score", "result", "confirm", "standing", "beschiss", "bomb", "call", "position", "points", "victory",
        )
        val credentialFragments = listOf("owner", "token", "credential", "password", "accesslink", "link", "session")
        (resultFragments + credentialFragments).forEach { fragment ->
            assertFalse(raw.contains(fragment), "public pairings must not mention '$fragment': $body")
        }
    }

    private fun createTournament(name: String): String {
        val body = mapOf("name" to name, "description" to "", "maxTeams" to 16)
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)["id"].asText()
    }

    private fun setPublicVisibility(id: String, publicVisibility: Boolean) {
        mockMvc.perform(
            put("/api/tournaments/$id/public-visibility")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"publicVisibility":$publicVisibility}"""),
        ).andExpect(status().isOk)
    }

    private fun setStatus(id: String, status: String) {
        jdbcTemplate.update("UPDATE tournaments SET status = ? WHERE id = ?", status, id)
    }

    private fun createTeam(tournamentId: String, name: String): JsonNode {
        val response = mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to name,
                            "playerOneName" to "$name 1",
                            "playerTwoName" to "$name 2",
                        ),
                    ),
                ),
        ).andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)
    }
}
