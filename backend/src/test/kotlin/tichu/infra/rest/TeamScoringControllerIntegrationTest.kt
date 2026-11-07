package tichu.infra.rest

import tichu.domain.AdminUserId
import tichu.domain.GameRepository
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.insertAdminRow
import tichu.testsupport.loginAsSeededAdmin
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Integration test for the team-session scoring reads (S5c, #101; ADR 0005),
 * driven over MockMvc and the real security chain against Testcontainers
 * PostgreSQL. Covers: current-table matchup/roster/lock/settings, the games list
 * reusing the write-response shape, and the session-scoping guards (seat check,
 * anonymous, admin, foreign tournament).
 */
@SpringBootTest(classes = [Application::class])
class TeamScoringControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: tichu.domain.AdminUserRepository,
    @Autowired private val passwordHasher: tichu.domain.PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val confirmationRepository: tichu.domain.TableConfirmationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-scoring-read"
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
            tichu.domain.Player(pid(p1), TournamentId(tournamentId), TeamId(id), "P1 of $name"),
            tichu.domain.Player(pid(p2), TournamentId(tournamentId), TeamId(id), "P2 of $name"),
        ),
    )

    private fun round(number: Int) = Round(
        id = RoundId("r-$number"),
        tournamentId = TournamentId(tournamentId),
        number = number,
        pairing = tichu.domain.Pairing(tables = listOf(Table(TableId(1), listOf(TeamId(teamA), TeamId(teamB))))),
    )

    private fun pid(value: String) = tichu.domain.PlayerId(value)

    private fun exchangeTeamSession(teamId: String): Cookie {
        val tokenRepository = context.getBean(tichu.domain.TeamTokenRepository::class.java)
        val sessionTokens = context.getBean(tichu.domain.SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val now = Instant.now()
        tokenRepository.save(
            tichu.domain.TeamToken(
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

    private fun gamesUrl(roundNumber: Int = 1, tableNumber: Int = 1): String =
        "/api/team/tournaments/$tournamentId/rounds/$roundNumber/tables/$tableNumber/games"

    private fun createGame(gameNumber: Int) {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to gameNumber,
                "beschiss" to false,
                "version" to null,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to 55,
                        "players" to listOf(
                            mapOf("playerId" to playerA1, "position" to 1, "call" to "SMALL", "bombs" to 0),
                            mapOf("playerId" to playerA2, "position" to 3, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to teamB,
                        "baseScore" to 45,
                        "players" to listOf(
                            mapOf("playerId" to playerB1, "position" to 2, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to playerB2, "position" to 4, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )
        mockMvc.perform(
            post(gamesUrl()).cookie(teamCookie).contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isCreated)
    }

    // ---- current-table ----------------------------------------------------------

    @Test
    fun `current-table reports who confirmed`() {
        confirmationRepository.add(
            tichu.domain.TableKey(TournamentId(tournamentId), 1, 1),
            TeamId(teamB),
            Instant.parse("2026-01-01T13:00:00Z"),
        )

        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMING"))
            .andExpect(jsonPath("$.confirmedTeamIds[0]").value(teamB))
            .andExpect(jsonPath("$.confirmedTeamIds.length()").value(1))
    }

    @Test
    fun `current-table returns the matchup, roster, confirmation state and settings`() {
        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tournamentId").value(tournamentId))
            .andExpect(jsonPath("$.roundNumber").value(1))
            .andExpect(jsonPath("$.tableNumber").value(1))
            .andExpect(jsonPath("$.status").value("PLAYING"))
            .andExpect(jsonPath("$.confirmedTeamIds.length()").value(0))
            .andExpect(jsonPath("$.grandTichuBonus").value(200))
            .andExpect(jsonPath("$.teams[0].teamId").value(teamA))
            .andExpect(jsonPath("$.teams[0].teamName").value("Team Alpha"))
            .andExpect(jsonPath("$.teams[0].players[0].playerId").value(playerA1))
            .andExpect(jsonPath("$.teams[0].players[0].name").value("P1 of Team Alpha"))
            .andExpect(jsonPath("$.teams[1].teamId").value(teamB))
            .andExpect(jsonPath("$.teams[1].teamName").value("Team Beta"))
            .andExpect(jsonPath("$.trio").value(false))
            .andExpect(jsonPath("$.schedule.length()").value(4))
            .andExpect(jsonPath("$.schedule[3].gameNumber").value(4))
            .andExpect(jsonPath("$.schedule[3].teamIds").value(contains(teamA, teamB)))
    }

    @Test
    fun `current-table at a trio table lists all three teams and the six-game schedule`() {
        teamRepository.save(team("team-c", "Team Gamma", "team-c-p1", "team-c-p2"))
        roundRepository.save(
            round(1).copy(
                pairing = tichu.domain.Pairing(
                    tables = listOf(Table(TableId(1), listOf(TeamId(teamA), TeamId(teamB), TeamId("team-c")))),
                ),
            ),
        )
        val teamCCookie = exchangeTeamSession("team-c")

        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trio").value(true))
            .andExpect(jsonPath("$.teams.length()").value(3))
            .andExpect(jsonPath("$.teams[2].teamName").value("Team Gamma"))
            .andExpect(jsonPath("$.teams[2].players[1].playerId").value("team-c-p2"))
            .andExpect(jsonPath("$.schedule.length()").value(6))
            .andExpect(jsonPath("$.schedule[0].teamIds").value(contains(teamA, teamB)))
            .andExpect(jsonPath("$.schedule[1].teamIds").value(contains(teamA, "team-c")))
            .andExpect(jsonPath("$.schedule[5].gameNumber").value(6))
            .andExpect(jsonPath("$.schedule[5].teamIds").value(contains(teamB, "team-c")))
    }

    @Test
    fun `current-table exposes the grand tichu bonus so the form's totals match`() {
        tournamentRepository.save(
            tournamentRepository.findById(TournamentId(tournamentId))!!
                .copy(settings = TournamentSettings(grandTichuBonus = 100)),
        )

        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grandTichuBonus").value(100))
    }

    @Test
    fun `current-table exposes the double win bonus so the form's totals match`() {
        tournamentRepository.save(
            tournamentRepository.findById(TournamentId(tournamentId))!!
                .copy(settings = TournamentSettings(doubleWinBonus = 100)),
        )

        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.doubleWinBonus").value(100))
    }

    @Test
    fun `current-table picks the highest-numbered round the team is seated at`() {
        // A later round seats the same two teams at table 1 again; "current" is round 2.
        roundRepository.save(
            Round(
                id = RoundId("r-2"),
                tournamentId = TournamentId(tournamentId),
                number = 2,
                pairing = tichu.domain.Pairing(
                    tables = listOf(Table(TableId(1), listOf(TeamId(teamB), TeamId(teamA)))),
                ),
            ),
        )

        mockMvc.perform(get("/api/team/me/current-table").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roundNumber").value(2))
    }

    @Test
    fun `current-table is 404 when the team is not seated at any round`() {
        teamRepository.save(team("team-x", "Team X", "team-x-p1", "team-x-p2"))
        val outsiderCookie = exchangeTeamSession("team-x")

        mockMvc.perform(get("/api/team/me/current-table").cookie(outsiderCookie))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `current-table rejects an anonymous caller with 401`() {
        mockMvc.perform(get("/api/team/me/current-table"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `current-table rejects an admin session - team endpoints are team-only`() {
        val adminCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        mockMvc.perform(get("/api/team/me/current-table").cookie(adminCookie))
            .andExpect(status().isForbidden)
    }

    // ---- games list --------------------------------------------------------------

    @Test
    fun `games lists entered games with the derived outcome, ordered by game number`() {
        createGame(2)
        createGame(1)

        mockMvc.perform(get(gamesUrl()).cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].gameNumber").value(1))
            .andExpect(jsonPath("$[1].gameNumber").value(2))
            .andExpect(jsonPath("$[0].version").value(1))
            .andExpect(jsonPath("$[0].outcome.teamPoints.$teamA").value(155))
            .andExpect(jsonPath("$[0].outcome.victoryPoints.$teamA").value(2))
            .andExpect(jsonPath("$[0].teams[0].players[0].tichuSuccess").value(true))
    }

    @Test
    fun `games is an empty list when none are entered`() {
        mockMvc.perform(get(gamesUrl()).cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `games rejects a team not seated at the table with 403`() {
        teamRepository.save(team("team-x", "Team X", "team-x-p1", "team-x-p2"))
        val outsiderCookie = exchangeTeamSession("team-x")

        mockMvc.perform(get(gamesUrl()).cookie(outsiderCookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `games is 404 for an unknown round or table`() {
        mockMvc.perform(get(gamesUrl(roundNumber = 9)).cookie(teamCookie))
            .andExpect(status().isNotFound)
        mockMvc.perform(get(gamesUrl(tableNumber = 9)).cookie(teamCookie))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `games is 404 when the path names a foreign tournament`() {
        mockMvc.perform(
            get("/api/team/tournaments/some-other-tournament/rounds/1/tables/1/games").cookie(teamCookie),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `games rejects an anonymous caller with 401`() {
        mockMvc.perform(get(gamesUrl()))
            .andExpect(status().isUnauthorized)
    }
}
