package com.example.template.infra.rest

import com.example.template.domain.AdminUserId
import com.example.template.domain.GameRepository
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
import com.example.template.testsupport.loginAsSeededAdmin
import tools.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

/**
 * Integration test for the team scoring API (#100, S5b), driven over MockMvc and
 * the real security chain against Testcontainers PostgreSQL. Covers the issue's
 * acceptance criteria: error-code mapping, optimistic version race, POST/PUT
 * split, round-lock rejection, seat authorization and the playing-teams rule.
 */
@SpringBootTest(classes = [Application::class])
class GameControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: com.example.template.domain.AdminUserRepository,
    @Autowired private val passwordHasher: com.example.template.domain.PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-scoring"
    private val teamA = "team-a"
    private val teamB = "team-b"
    private val playerA1 = "team-a-p1"
    private val playerA2 = "team-a-p2"
    private val playerB1 = "team-b-p1"
    private val playerB2 = "team-b-p2"

    private lateinit var teamCookie: Cookie

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
        teamRepository.save(team(teamA, "Team Alpha", playerA1, playerA2))
        teamRepository.save(team(teamB, "Team Beta", playerB1, playerB2))
        roundRepository.save(round(1))

        teamCookie = exchangeTeamSession(teamA)
    }

    private fun team(id: String, name: String, p1: String, p2: String) = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournamentId),
        name = name,
        players = listOf(
            com.example.template.domain.Player(PlayerId(p1), TournamentId(tournamentId), TeamId(id), "P1 of $name"),
            com.example.template.domain.Player(PlayerId(p2), TournamentId(tournamentId), TeamId(id), "P2 of $name"),
        ),
    )

    private fun round(number: Int, locked: Boolean = false) = Round(
        id = RoundId("r-$number"),
        tournamentId = TournamentId(tournamentId),
        number = number,
        pairing = com.example.template.domain.Pairing(tables = listOf(Table(TableId(1), TeamId(teamA), TeamId(teamB)))),
        locked = locked,
    )

    private fun PlayerId(value: String) = com.example.template.domain.PlayerId(value)

    private fun gameKey() = com.example.template.domain.GameKey(TournamentId(tournamentId), 1, 1, 1)

    /** Exchanges a known team token for a team-session cookie. */
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
            post("/api/team/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("exchange did not set a session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    private fun createBody(
        gameNumber: Int = 1,
        beschiss: Boolean = false,
        version: Int? = null,
        baseA: Int = 55,
        baseB: Int = 45,
        teamBPlayers: Pair<String, String> = playerB1 to playerB2,
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "gameNumber" to gameNumber,
            "beschiss" to beschiss,
            "version" to version,
            "teams" to listOf(
                mapOf(
                    "teamId" to teamA,
                    "baseScore" to baseA,
                    "players" to listOf(
                        mapOf("playerId" to playerA1, "position" to 1, "call" to "SMALL", "bombs" to 0),
                        mapOf("playerId" to playerA2, "position" to 3, "call" to "NONE", "bombs" to 0),
                    ),
                ),
                mapOf(
                    "teamId" to teamB,
                    "baseScore" to baseB,
                    "players" to listOf(
                        mapOf("playerId" to teamBPlayers.first, "position" to 2, "call" to "NONE", "bombs" to 0),
                        mapOf("playerId" to teamBPlayers.second, "position" to 4, "call" to "NONE", "bombs" to 0),
                    ),
                ),
            ),
        ),
    )

    private fun createUrl(roundNumber: Int = 1, tableNumber: Int = 1): String =
        "/api/team/tournaments/$tournamentId/rounds/$roundNumber/tables/$tableNumber/games"

    private fun updateUrl(gameNumber: Int, roundNumber: Int = 1, tableNumber: Int = 1): String =
        "${createUrl(roundNumber, tableNumber)}/$gameNumber"

    private fun createGame(body: String = createBody()) = mockMvc.perform(
        post(createUrl()).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun updateGame(gameNumber: Int = 1, body: String = createBody(version = 1)) = mockMvc.perform(
        put(updateUrl(gameNumber)).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    // ---- happy path -----------------------------------------------------------

    @Test
    fun `POST creates the game and returns the server-authoritative outcome`() {
        createGame()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.gameNumber").value(1))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.outcome.doubleWinTeam").doesNotExist())
            .andExpect(jsonPath("$.outcome.teamPoints.$teamA").value(155))
            .andExpect(jsonPath("$.outcome.teamPoints.$teamB").value(45))
            .andExpect(jsonPath("$.outcome.victoryPoints.$teamA").value(2))
            .andExpect(jsonPath("$.outcome.tichuSuccesses[0]").value(playerA1))
            // The full echo: stored raw facts as the server sees them.
            .andExpect(jsonPath("$.beschiss").value(false))
            .andExpect(jsonPath("$.teams[0].teamId").value(teamA))
            .andExpect(jsonPath("$.teams[0].baseScore").value(55))
            .andExpect(jsonPath("$.teams[0].players[0].playerId").value(playerA1))
            .andExpect(jsonPath("$.teams[0].players[0].position").value(1))
            .andExpect(jsonPath("$.teams[0].players[0].call").value("SMALL"))
            .andExpect(jsonPath("$.teams[0].players[0].bombs").value(0))
            .andExpect(jsonPath("$.teams[0].players[0].tichuSuccess").value(true))
            .andExpect(jsonPath("$.teams[1].players[1].tichuSuccess").value(false))
    }

    @Test
    fun `PUT edits the game and bumps the version`() {
        createGame().andExpect(status().isCreated)

        updateGame(body = createBody(version = 1, baseA = 70, baseB = 30))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.outcome.teamPoints.$teamA").value(170))

        val stored = gameRepository.find(gameKey())!!
        org.junit.jupiter.api.Assertions.assertEquals(70, stored.baseScoreTeam1)
    }

    // ---- AC: POST on existing game, create race -------------------------------

    @Test
    fun `POST on an existing game answers GAME_ALREADY_EXISTS as 409`() {
        createGame().andExpect(status().isCreated)

        createGame()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_ALREADY_EXISTS"))
    }

    @Test
    fun `a concurrent double-POST is resolved by the unique constraint - exactly one 201`() {
        // Both requests race for the same natural-key slot before either commits;
        // the unique constraint arbitrates and the loser must see the same 409.
        val barrier = java.util.concurrent.CountDownLatch(2)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val codes = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        try {
            val futures = (1..2).map {
                executor.submit {
                    barrier.countDown()
                    barrier.await()
                    val status = mockMvc.perform(
                        post(createUrl()).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON).content(createBody()),
                    ).andReturn().response.status
                    codes.add(status)
                }
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        org.junit.jupiter.api.Assertions.assertEquals(setOf(201, 409), codes.toSet(), "one creator, one loser")
        org.junit.jupiter.api.Assertions.assertEquals(1, countGameRows(), "the constraint allowed exactly one row")
    }

    private fun countGameRows(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM games", Int::class.java)!!

    // ---- AC: version race, first commit wins ----------------------------------

    @Test
    fun `stale version loses the race with GAME_CONCURRENT_MODIFICATION - first commit wins`() {
        createGame().andExpect(status().isCreated)

        updateGame(body = createBody(version = 1, baseA = 60, baseB = 40))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(2))

        updateGame(body = createBody(version = 1, baseA = 30, baseB = 70))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONCURRENT_MODIFICATION"))

        val stored = gameRepository.find(gameKey())!!
        org.junit.jupiter.api.Assertions.assertEquals(2, stored.version)
        org.junit.jupiter.api.Assertions.assertEquals(60, stored.baseScoreTeam1, "first commit wins")
    }

    // ---- AC: round lock ---------------------------------------------------------

    @Test
    fun `POST and PUT in a locked round answer GAME_CONFIRMED as 409`() {
        roundRepository.save(round(1, locked = true))

        createGame()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONFIRMED"))

        updateGame()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONFIRMED"))
    }

    // ---- AC: seat authorization -------------------------------------------------

    @Test
    fun `a team not seated at the table is rejected with 403`() {
        teamRepository.save(team("team-x", "Team X", "team-x-p1", "team-x-p2"))
        val outsiderCookie = exchangeTeamSession("team-x")

        mockMvc.perform(
            post(createUrl()).cookie(outsiderCookie).contentType(MediaType.APPLICATION_JSON).content(createBody()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `an anonymous caller is rejected with 401`() {
        mockMvc.perform(post(createUrl()).contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `an admin session cannot create games - team endpoints are team-only`() {
        val adminCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        mockMvc.perform(
            post(createUrl()).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(createBody()),
        )
            .andExpect(status().isForbidden)
    }

    // ---- AC: submitted teams must be the playing teams --------------------------

    @Test
    fun `submitting a team that is not at the table answers GAME_VALIDATION_INVALID`() {
        teamRepository.save(team("team-c", "Team C", "team-c-p1", "team-c-p2"))
        val wrongTeamBody = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to 1,
                "beschiss" to false,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to 55,
                        "players" to listOf(
                            mapOf("playerId" to playerA1, "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerA2, "position" to 2, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to "team-c",
                        "baseScore" to 45,
                        "players" to listOf(
                            mapOf("playerId" to "team-c-p1", "position" to 3, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "team-c-p2", "position" to 4, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )

        createGame(body = wrongTeamBody)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GAME_VALIDATION_INVALID"))
            .andExpect(jsonPath("$.field").value("teams"))
    }

    @Test
    fun `a player not on the submitted team answers GAME_VALIDATION_INVALID`() {
        val intruderBody = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to 1,
                "beschiss" to false,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to 55,
                        "players" to listOf(
                            mapOf("playerId" to playerA1, "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerB1, "position" to 2, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to teamB,
                        "baseScore" to 45,
                        "players" to listOf(
                            mapOf("playerId" to playerA2, "position" to 3, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerB2, "position" to 4, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )

        createGame(body = intruderBody)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GAME_VALIDATION_INVALID"))
    }

    // ---- engine validation surfaces as GAME_VALIDATION_INVALID ------------------

    @Test
    fun `base scores that do not sum to 100 answer GAME_VALIDATION_INVALID anchored to baseScore`() {
        createGame(body = createBody(baseA = 60, baseB = 45))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GAME_VALIDATION_INVALID"))
            .andExpect(jsonPath("$.field").value("baseScore"))
    }

    @Test
    fun `grand Tichu is rejected when the tournament setting disables it`() {
        tournamentRepository.save(
            tournamentRepository.findById(TournamentId(tournamentId))!!
                .copy(settings = TournamentSettings(allowGrandTichu = false)),
        )
        val grandBody = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to 1,
                "beschiss" to false,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to 55,
                        "players" to listOf(
                            mapOf("playerId" to playerA1, "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerA2, "position" to 2, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to teamB,
                        "baseScore" to 45,
                        "players" to listOf(
                            mapOf("playerId" to playerB1, "position" to 3, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerB2, "position" to 4, "call" to "GRAND", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )

        createGame(body = grandBody)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GAME_VALIDATION_INVALID"))
    }

    // ---- not-found paths ---------------------------------------------------------

    @Test
    fun `PUT to an uncreated game answers GAME_NOT_FOUND as 404`() {
        updateGame()
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"))
    }

    @Test
    fun `addressing an unknown table or round answers GAME_NOT_FOUND`() {
        createGame(body = createBody()).andExpect(status().isCreated) // sanity: the fixture works

        mockMvc.perform(
            post(createUrl(tableNumber = 9)).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON)
                .content(createBody()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"))

        mockMvc.perform(
            post(createUrl(roundNumber = 7)).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON)
                .content(createBody()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"))
    }

    // ---- double win derived end-to-end -------------------------------------------

    @Test
    fun `a double win is derived server-side - base forced to 0-0, losers without position`() {
        val doubleWinBody = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to 1,
                "beschiss" to false,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to 0,
                        "players" to listOf(
                            mapOf("playerId" to playerA1, "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerA2, "position" to 2, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to teamB,
                        "baseScore" to 0,
                        "players" to listOf(
                            mapOf("playerId" to playerB1, "position" to null, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerB2, "position" to null, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )

        createGame(body = doubleWinBody)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.outcome.doubleWinTeam").value(teamA))
            .andExpect(jsonPath("$.outcome.teamPoints.$teamA").value(200))
            .andExpect(jsonPath("$.outcome.victoryPoints.$teamA").value(3))
            .andExpect(jsonPath("$.outcome.victoryPoints.$teamB").value(0))
    }
}
