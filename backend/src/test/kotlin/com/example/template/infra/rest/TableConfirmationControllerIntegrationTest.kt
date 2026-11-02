package com.example.template.infra.rest

import com.example.template.domain.AdminUserId
import com.example.template.domain.GameKey
import com.example.template.domain.GameRepository
import com.example.template.domain.Pairing
import com.example.template.domain.Player
import com.example.template.domain.PlayerId
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TableId
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
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.contains
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration test for the confirm/retract API (S6a, #105; ADR 0006), driven over
 * MockMvc and the real security chain against Testcontainers PostgreSQL. Games
 * are entered over HTTP too, so the freeze is observed end to end.
 */
@SpringBootTest(classes = [Application::class])
class TableConfirmationControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-confirm"
    private val teamA = "team-a"
    private val teamB = "team-b"
    private val teamC = "team-c"

    private val cookies = mutableMapOf<String, Cookie>()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        insertAdminRow(jdbcTemplate, "admin-owner", "[EMAIL]")
        tournamentRepository.save(
            Tournament(
                id = TournamentId(tournamentId),
                ownerId = AdminUserId("admin-owner"),
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        listOf(teamA, teamB, teamC, "team-x").forEach { teamRepository.save(team(it)) }
        seat(teamA, teamB)
        cookies.clear()
    }

    private fun team(id: String) = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournamentId),
        name = "Team $id",
        players = listOf(
            Player(PlayerId("$id-p1"), TournamentId(tournamentId), TeamId(id), "P1 $id"),
            Player(PlayerId("$id-p2"), TournamentId(tournamentId), TeamId(id), "P2 $id"),
        ),
    )

    private fun seat(vararg teams: String) {
        roundRepository.save(
            Round(RoundId("r-1"), TournamentId(tournamentId), 1, Pairing(listOf(Table(TableId(1), teams.map(::TeamId))))),
        )
    }

    private fun cookieOf(teamId: String): Cookie = cookies.getOrPut(teamId) { exchangeTeamSession(teamId) }

    private fun exchangeTeamSession(teamId: String): Cookie {
        val tokenRepository = context.getBean(com.example.template.domain.TeamTokenRepository::class.java)
        val sessionTokens = context.getBean(com.example.template.domain.SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val now = Instant.now()
        tokenRepository.save(
            com.example.template.domain.TeamToken(
                teamId = TeamId(teamId),
                tokenHash = sessionTokens.hash(raw),
                revealed = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val result = mockMvc.perform(
            post("/api/team/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("exchange did not set a session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    private val tableUrl = "/api/team/tournaments/$tournamentId/rounds/1/tables/1"

    private fun gameBody(gameNumber: Int, first: String, second: String, version: Int? = null): String =
        objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to gameNumber,
                "beschiss" to false,
                "version" to version,
                "teams" to listOf(first, second).mapIndexed { i, id ->
                    mapOf(
                        "teamId" to id,
                        "baseScore" to if (i == 0) 60 else 40,
                        "players" to listOf(
                            mapOf("playerId" to "$id-p1", "position" to 1 + i, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "$id-p2", "position" to 3 + i, "call" to "NONE", "bombs" to 0),
                        ),
                    )
                },
            ),
        )

    /** Enters game [gameNumber] as its first playing team; returns the response. */
    private fun enterGame(gameNumber: Int, first: String = teamA, second: String = teamB): ResultActions =
        mockMvc.perform(
            post("$tableUrl/games").cookie(cookieOf(first))
                .contentType(MediaType.APPLICATION_JSON).content(gameBody(gameNumber, first, second)),
        )

    private fun editGame(gameNumber: Int, version: Int, by: String = teamB): ResultActions =
        mockMvc.perform(
            put("$tableUrl/games/$gameNumber").cookie(cookieOf(by))
                .contentType(MediaType.APPLICATION_JSON).content(gameBody(gameNumber, teamA, teamB, version)),
        )

    /** The stored versions, i.e. what a team that just reloaded has seen. */
    private fun seenBody(count: Int): String = objectMapper.writeValueAsString(
        mapOf(
            "games" to (1..count).map {
                val version = gameRepository.find(GameKey(TournamentId(tournamentId), 1, 1, it))?.version ?: 1
                mapOf("gameNumber" to it, "version" to version)
            },
        ),
    )

    private fun confirm(teamId: String, body: String = seenBody(4)): ResultActions = mockMvc.perform(
        put("$tableUrl/confirmation").cookie(cookieOf(teamId)).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun retract(teamId: String): ResultActions =
        mockMvc.perform(delete("$tableUrl/confirmation").cookie(cookieOf(teamId)))

    private fun enterNormalGames() = (1..4).forEach { enterGame(it).andExpect(status().isCreated) }

    @Test
    fun `a normal table moves through playing, confirming and completed`() {
        enterNormalGames()

        confirm(teamA)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMING"))
            .andExpect(jsonPath("$.confirmedTeamIds").value(contains(teamA)))
        confirm(teamB)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.confirmedTeamIds").value(contains(teamA, teamB)))
    }

    @Test
    fun `a trio table completes with the third confirmation`() {
        seat(teamA, teamB, teamC)
        // 1 AB, 2 AC, 3 BC, 4 AB, 5 AC, 6 BC
        listOf(1 to (teamA to teamB), 2 to (teamA to teamC), 3 to (teamB to teamC),
            4 to (teamA to teamB), 5 to (teamA to teamC), 6 to (teamB to teamC))
            .forEach { (n, pair) -> enterGame(n, pair.first, pair.second).andExpect(status().isCreated) }

        confirm(teamC, seenBody(6)).andExpect(jsonPath("$.status").value("CONFIRMING"))
        confirm(teamA, seenBody(6)).andExpect(jsonPath("$.status").value("CONFIRMING"))
        confirm(teamB, seenBody(6))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.confirmedTeamIds").value(contains(teamA, teamB, teamC)))
    }

    @Test
    fun `confirming with games missing answers TABLE_INCOMPLETE`() {
        (1..3).forEach { enterGame(it).andExpect(status().isCreated) }

        confirm(teamA, seenBody(3))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TABLE_INCOMPLETE"))
    }

    @Test
    fun `confirming over a stale version answers GAME_CONCURRENT_MODIFICATION`() {
        enterNormalGames()
        val stale = seenBody(4)
        editGame(2, version = 1).andExpect(status().isOk)

        confirm(teamA, stale)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONCURRENT_MODIFICATION"))
    }

    @Test
    fun `a confirm body without games is rejected as invalid input`() {
        enterNormalGames()

        confirm(teamA, "{}").andExpect(status().isBadRequest)
    }

    @Test
    fun `games are frozen while confirming and once completed`() {
        enterNormalGames()
        confirm(teamA).andExpect(status().isOk)

        editGame(1, version = 1)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONFIRMED"))

        confirm(teamB).andExpect(jsonPath("$.status").value("COMPLETED"))
        editGame(1, version = 1)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONFIRMED"))
    }

    @Test
    fun `retracting the last confirmation returns to playing and edits work again`() {
        enterNormalGames()
        confirm(teamA).andExpect(status().isOk)

        retract(teamA)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PLAYING"))
            .andExpect(jsonPath("$.confirmedTeamIds.length()").value(0))
        editGame(1, version = 1).andExpect(status().isOk)
    }

    @Test
    fun `retracting on a completed table answers TABLE_COMPLETED`() {
        enterNormalGames()
        confirm(teamA).andExpect(status().isOk)
        confirm(teamB).andExpect(status().isOk)

        retract(teamA)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TABLE_COMPLETED"))
    }

    @Test
    fun `confirming twice and retracting when not confirmed answer 200 and change nothing`() {
        enterNormalGames()
        confirm(teamA).andExpect(status().isOk)

        confirm(teamA)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.confirmedTeamIds").value(contains(teamA)))
        retract(teamB)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMING"))
            .andExpect(jsonPath("$.confirmedTeamIds").value(contains(teamA)))
    }

    @Test
    fun `a team not seated at the table gets 403 on confirm and retract`() {
        enterNormalGames()

        confirm("team-x").andExpect(status().isForbidden)
        retract("team-x").andExpect(status().isForbidden)
    }

    // ---- concurrency ------------------------------------------------------------

    @Test
    fun `a confirm waits for a concurrent edit holding the table lock and then sees it`() {
        enterNormalGames()
        val stale = seenBody(4)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            // An edit in flight: holds the table lock and has bumped game 2, uncommitted.
            val edit = executor.submit {
                transactionTemplate.executeWithoutResult {
                    roundRepository.lockTable(com.example.template.domain.TableKey(TournamentId(tournamentId), 1, 1))
                    jdbcTemplate.update(
                        "UPDATE games SET version = version + 1 WHERE tournament_id = ? AND game_number = 2",
                        tournamentId,
                    )
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            locked.await(10, TimeUnit.SECONDS)

            val confirmStatus = executor.submit<Pair<Int, String>> {
                val response = confirm(teamA, stale).andReturn().response
                response.status to response.contentAsString
            }
            Thread.sleep(500)
            assertFalse(confirmStatus.isDone, "the confirm blocks on the table lock")

            release.countDown()
            edit.get(10, TimeUnit.SECONDS)
            val (status, body) = confirmStatus.get(10, TimeUnit.SECONDS)

            assertEquals(409, status, "the confirm saw the committed edit")
            assertEquals("GAME_CONCURRENT_MODIFICATION", objectMapper.readTree(body)["code"].asString())
            assertEquals(0, confirmationRows())
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    @Test
    fun `an edit waits for a concurrent confirm holding the table lock and is then rejected as confirmed`() {
        enterNormalGames()
        cookieOf(teamB)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            // A confirm in flight: holds the table lock and has recorded team A, uncommitted.
            val confirmation = executor.submit {
                transactionTemplate.executeWithoutResult {
                    roundRepository.lockTable(com.example.template.domain.TableKey(TournamentId(tournamentId), 1, 1))
                    jdbcTemplate.update(
                        "INSERT INTO table_confirmations (tournament_id, round_number, table_number, team_id, confirmed_at) " +
                            "VALUES (?, 1, 1, ?, now())",
                        tournamentId, teamA,
                    )
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            locked.await(10, TimeUnit.SECONDS)

            val edit = executor.submit<Pair<Int, String>> {
                val response = editGame(1, version = 1).andReturn().response
                response.status to response.contentAsString
            }
            Thread.sleep(500)
            assertFalse(edit.isDone, "the edit blocks on the table lock")

            release.countDown()
            confirmation.get(10, TimeUnit.SECONDS)
            val (status, body) = edit.get(10, TimeUnit.SECONDS)

            assertEquals(409, status, "the edit saw the committed confirmation")
            assertEquals("GAME_CONFIRMED", objectMapper.readTree(body)["code"].asString())
            assertEquals(1, gameRepository.find(GameKey(TournamentId(tournamentId), 1, 1, 1))!!.version)
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    @Test
    fun `simultaneous confirms from both teams both commit and complete the table`() {
        // A smoke test of the happy race; the two lock tests above prove the serialization.
        enterNormalGames()
        val body = seenBody(4)
        cookieOf(teamA)
        cookieOf(teamB)
        val barrier = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val statuses = try {
            listOf(teamA, teamB).map { team ->
                executor.submit<Int> {
                    barrier.countDown()
                    barrier.await()
                    confirm(team, body).andReturn().response.status
                }
            }.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        assertEquals(listOf(200, 200), statuses)
        assertEquals(2, confirmationRows())
        retract(teamA)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TABLE_COMPLETED"))
    }

    private fun confirmationRows(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM table_confirmations", Int::class.java)!!
}
