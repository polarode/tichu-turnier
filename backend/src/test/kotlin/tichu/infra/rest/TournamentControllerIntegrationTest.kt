package tichu.infra.rest

import tichu.domain.AdminUserRepository
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.GameRepository
import tichu.domain.PasswordHasher
import tichu.domain.PlayerResult
import tichu.domain.RoundRepository
import tichu.domain.TeamRepository
import tichu.domain.TeamResult
import tichu.domain.TichuCall
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.loginAsSeededAdmin
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test: boots the real application context (all beans wired through the
 * `*Configuration` classes) against Testcontainers PostgreSQL and drives the
 * controller over MockMvc through the real security chain.
 */
@SpringBootTest(classes = [tichu.main.Application::class])
class TournamentControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val confirmationRepository: tichu.domain.TableConfirmationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var sessionCookie: Cookie
    private lateinit var otherAdminCookie: Cookie

    @BeforeEach
    fun authenticate() {
        sessionCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
        otherAdminCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
    }

    @Test
    fun `create then fetch a tournament`() {
        val id = createTournament("Spring Classic", "annual invitational")

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Spring Classic"))
            .andExpect(jsonPath("$.status").value("SETUP"))
            .andExpect(jsonPath("$.maxTeams").value(16))
            .andExpect(jsonPath("$.settings.grandTichuBonus").value(200))
            .andExpect(jsonPath("$.settings.doubleWinBonus").value(200))
    }

    @Test
    fun `create persists the double win bonus from the request body`() {
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Small Double Win","maxTeams":16,"settings":{"doubleWinBonus":100}}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        val id = objectMapper.readTree(response)["id"].asText()

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settings.doubleWinBonus").value(100))
    }

    @Test
    fun `create persists the grand tichu bonus from the request body`() {
        val id = createTournament("Small Grand Tichu", "", grandTichuBonus = 100)

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settings.grandTichuBonus").value(100))
    }

    @Test
    fun `list returns created tournaments`() {
        createTournament("A")
        createTournament("B")

        mockMvc.perform(get("/api/tournaments").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `create rejects a blank name with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  ","maxTeams":16}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("name"))
    }

    @Test
    fun `create rejects a grand tichu bonus other than 100 or 200 with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic","maxTeams":16,"settings":{"grandTichuBonus":150}}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("grandTichuBonus"))
    }

    @Test
    fun `create rejects a double win bonus other than 100 or 200 with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic","maxTeams":16,"settings":{"doubleWinBonus":150}}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("doubleWinBonus"))
    }

    @Test
    fun `create rejects a missing or non-positive maxTeams with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic","maxTeams":0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("maxTeams"))

        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("maxTeams"))
    }

    @Test
    fun `fetching an unknown tournament returns a 404 problem detail`() {
        mockMvc.perform(get("/api/tournaments/does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a foreign admin cannot read another admin's tournament`() {
        val id = createTournament("Alice's tournament", "private")

        mockMvc.perform(get("/api/tournaments/$id").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        // The owner's tournament is untouched throughout.
        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Alice's tournament"))
    }

    @Test
    fun `list returns only the caller's own tournaments`() {
        createTournament("alice-only")
        createTournamentAs(otherAdminCookie, "bob-only")

        mockMvc.perform(get("/api/tournaments").cookie(otherAdminCookie))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("bob-only")))
            .andExpect(content().string(not(containsString("alice-only"))))
    }

    @Test
    fun `unauthenticated requests get a 401 problem detail`() {
        mockMvc.perform(get("/api/tournaments"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `update replaces the tournament fields and persists them`() {
        val id = createTournament("Original", "old", grandTichuBonus = 200)

        mockMvc.perform(
            put("/api/tournaments/$id")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "Renamed",
                            "description" to "new description",
                            "maxTeams" to 8,
                            "settings" to mapOf("grandTichuBonus" to 100, "doubleWinBonus" to 100),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.description").value("new description"))
            .andExpect(jsonPath("$.maxTeams").value(8))
            .andExpect(jsonPath("$.settings.grandTichuBonus").value(100))
            .andExpect(jsonPath("$.settings.doubleWinBonus").value(100))

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.settings.grandTichuBonus").value(100))
            .andExpect(jsonPath("$.settings.doubleWinBonus").value(100))
    }

    @Test
    fun `update rejects invalid fields with a field-anchored 400`() {
        val id = createTournament("Original")

        mockMvc.perform(
            put("/api/tournaments/$id")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  ","maxTeams":16}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("name"))

        mockMvc.perform(
            put("/api/tournaments/$id")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic","maxTeams":0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.field").value("maxTeams"))
    }

    @Test
    fun `update is rejected once the tournament has started`() {
        val id = createTournament("Started")
        setStatus(id, "ACTIVE")

        mockMvc.perform(
            put("/api/tournaments/$id")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateBody("too late")),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_EDITABLE"))
    }

    @Test
    fun `a foreign admin cannot update another admin's tournament`() {
        val id = createTournament("Alice's tournament")

        mockMvc.perform(
            put("/api/tournaments/$id")
                .cookie(otherAdminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateBody("hijacked")),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Alice's tournament"))
    }

    @Test
    fun `update of an unknown tournament returns 404`() {
        mockMvc.perform(
            put("/api/tournaments/does-not-exist")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateBody("nowhere")),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `delete removes the caller's tournament`() {
        val id = createTournament("Doomed")

        mockMvc.perform(delete("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a foreign admin cannot delete another admin's tournament`() {
        val id = createTournament("Alice's tournament")

        mockMvc.perform(delete("/api/tournaments/$id").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
    }

    @Test
    fun `delete of an unknown tournament returns 404`() {
        mockMvc.perform(delete("/api/tournaments/does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `unauthenticated update and delete get 401`() {
        mockMvc.perform(
            put("/api/tournaments/any")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateBody("any")),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(delete("/api/tournaments/any"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `starting a tournament transitions it to active and returns round 1 with its tables`() {
        val id = createTournament("Spring Classic")
        createTeam(id, "Team Alpha")
        createTeam(id, "Team Beta")
        createTeam(id, "Team Gamma")
        createTeam(id, "Team Delta")

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tournament.status").value("ACTIVE"))
            .andExpect(jsonPath("$.round.roundNumber").value(1))
            .andExpect(jsonPath("$.round.tables.length()").value(2))
            .andExpect(jsonPath("$.round.tables[0].tableNumber").value(1))
            .andExpect(jsonPath("$.round.tables[1].tableNumber").value(2))
            // A fresh round: the game count is known, no games entered yet.
            .andExpect(jsonPath("$.round.tables[0].gameCount").value(4))
            .andExpect(jsonPath("$.round.tables[0].games.length()").value(0))

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `starting an already started tournament returns a 409 problem`() {
        val id = createTournament("Twice")
        createTeam(id, "Team Alpha")
        createTeam(id, "Team Beta")
        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie)).andExpect(status().isOk)

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_STARTABLE"))
    }

    @Test
    fun `starting with fewer than two teams returns a 409 problem`() {
        val id = createTournament("Lonely")
        createTeam(id, "Team Alpha")

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_TEAMS"))
    }

    @ParameterizedTest
    @ValueSource(ints = [3, 5, 7])
    fun `starting with an odd team count seats one trio table at the last table number`(count: Int) {
        val id = createTournament("Odd $count")
        (1..count).forEach { createTeam(id, "Team $it") }
        val trioNumber = (count - 1) / 2

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.round.tables.length()").value(trioNumber))
            .andExpect(jsonPath("$.round.tables[-1].tableNumber").value(trioNumber))
            .andExpect(jsonPath("$.round.tables[-1].trio").value(true))
            .andExpect(jsonPath("$.round.tables[-1].teamIds.length()").value(3))
            .andExpect(jsonPath("$.round.tables[-1].gameCount").value(6))

        val persisted = roundRepository.findByTournamentAndNumber(TournamentId(id), 1)!!.pairing.tables
        assertEquals(listOf(trioNumber), persisted.filter { it.isTrio }.map { it.id.value })
        assertEquals(count, persisted.sumOf { it.seatedTeams.size })
    }

    @Test
    fun `a foreign admin cannot start another admin's tournament`() {
        val id = createTournament("Alice's tournament")
        createTeam(id, "Team Alpha")
        createTeam(id, "Team Beta")

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `rounds of a started tournament are listed in order with their tables`() {
        val id = createTournament("Spring Classic")
        createTeam(id, "Team Alpha")
        createTeam(id, "Team Beta")
        createTeam(id, "Team Gamma")
        createTeam(id, "Team Delta")

        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("\$[0].roundNumber").value(1))
            .andExpect(jsonPath("\$[0].tables.length()").value(2))
            .andExpect(jsonPath("\$[0].tables[0].tableNumber").value(1))
            .andExpect(jsonPath("\$[0].tables[0].teamIds.length()").value(2))
            .andExpect(jsonPath("\$[0].tables[0].trio").value(false))
            .andExpect(jsonPath("\$[0].tables[0].schedule.length()").value(4))
            .andExpect(jsonPath("\$[0].tables[0].schedule[3].gameNumber").value(4))
            .andExpect(jsonPath("\$[0].tables[0].schedule[3].teamIds.length()").value(2))
            .andExpect(jsonPath("\$[0].tables[1].tableNumber").value(2))
            .andExpect(jsonPath("\$[0].tables[1].status").value("PLAYING"))
            .andExpect(jsonPath("\$[0].tables[1].confirmedTeamIds.length()").value(0))
    }

    @Test
    fun `the rounds read exposes each table's confirmation status and confirmed teams`() {
        val id = createTournament("Spring Classic")
        listOf("Team Alpha", "Team Beta", "Team Gamma", "Team Delta").forEach { createTeam(id, it) }
        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)
        val (first, second) = roundRepository.findByTournamentAndNumber(TournamentId(id), 1)!!.table(1)!!.seatedTeams
        val tableOne = tichu.domain.TableKey(TournamentId(id), 1, 1)
        val at = java.time.Instant.parse("2026-01-01T13:00:00Z")
        confirmationRepository.add(tableOne, second, at)
        confirmationRepository.add(tableOne, first, at)

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].tables[0].status").value("COMPLETED"))
            .andExpect(jsonPath("\$[0].tables[0].confirmedTeamIds").value(contains(first.value, second.value)))
            .andExpect(jsonPath("\$[0].tables[1].status").value("PLAYING"))
    }

    @Test
    fun `the rounds read exposes a trio table's seated teams and six-game schedule`() {
        val id = createTournament("Trio")
        listOf("Team Alpha", "Team Beta", "Team Gamma").forEach { createTeam(id, it) }
        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)
        val (a, b, c) = roundRepository.findByTournamentAndNumber(TournamentId(id), 1)!!
            .table(1)!!.seatedTeams.map { it.value }

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].tables.length()").value(1))
            .andExpect(jsonPath("\$[0].tables[0].trio").value(true))
            .andExpect(jsonPath("\$[0].tables[0].teamIds").value(contains(a, b, c)))
            .andExpect(jsonPath("\$[0].tables[0].gameCount").value(6))
            .andExpect(jsonPath("\$[0].tables[0].schedule.length()").value(6))
            .andExpect(jsonPath("\$[0].tables[0].schedule[0].teamIds").value(contains(a, b)))
            .andExpect(jsonPath("\$[0].tables[0].schedule[1].teamIds").value(contains(a, c)))
            .andExpect(jsonPath("\$[0].tables[0].schedule[2].gameNumber").value(3))
            .andExpect(jsonPath("\$[0].tables[0].schedule[2].teamIds").value(contains(b, c)))
    }

    @Test
    fun `rounds carry each table's game count and entered games with the derived outcome`() {
        val id = createTournament("Spring Classic")
        listOf("Team Alpha", "Team Beta", "Team Gamma", "Team Delta").forEach { createTeam(id, it) }
        mockMvc.perform(post("/api/tournaments/$id/start").cookie(sessionCookie))
            .andExpect(status().isOk)
        val home = enterGameAtTableOne(id, gameNumber = 2, beschiss = true)

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].tables[0].gameCount").value(4))
            .andExpect(jsonPath("\$[0].tables[0].games.length()").value(1))
            .andExpect(jsonPath("\$[0].tables[0].games[0].gameNumber").value(2))
            .andExpect(jsonPath("\$[0].tables[0].games[0].beschiss").value(true))
            // Home: 55 base + successful small Tichu = 155 TP.
            .andExpect(jsonPath("\$[0].tables[0].games[0].outcome.teamPoints['$home']").value(155))
            .andExpect(jsonPath("\$[0].tables[1].games.length()").value(0))
    }

    /**
     * Records one game at table 1 of round 1 through the persistence port (the
     * team-session write flow is covered by GameControllerIntegrationTest) and
     * returns the home team id: home 55 base with a successful small Tichu.
     */
    private fun enterGameAtTableOne(tournamentId: String, gameNumber: Int, beschiss: Boolean): String {
        val tid = TournamentId(tournamentId)
        val table = roundRepository.findByTournamentAndNumber(tid, 1)!!.table(1)!!
        val (home, away) = table.seatedTeams.map { teamRepository.findById(it, tid)!! }
        transactionTemplate.execute {
            gameRepository.create(
                Game.record(
                    GameKey(tid, roundNumber = 1, tableNumber = 1, gameNumber = gameNumber),
                    listOf(
                        TeamResult(home.id, 55, listOf(
                            PlayerResult(home.players[0].id, 1, TichuCall.SMALL, 0),
                            PlayerResult(home.players[1].id, 3, TichuCall.NONE, 0),
                        )),
                        TeamResult(away.id, 45, listOf(
                            PlayerResult(away.players[0].id, 2, TichuCall.NONE, 0),
                            PlayerResult(away.players[1].id, 4, TichuCall.NONE, 0),
                        )),
                    ),
                    beschiss,
                    TournamentSettings(),
                ),
            )
        }
        return home.id.value
    }

    @Test
    fun `rounds of an unstarted tournament are an empty list`() {
        val id = createTournament("Empty")

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `a foreign admin cannot read another admin's rounds`() {
        val id = createTournament("Alice's tournament")

        mockMvc.perform(get("/api/tournaments/$id/rounds").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `unauthenticated rounds request gets 401`() {
        mockMvc.perform(get("/api/tournaments/any/rounds"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `public visibility toggles on and off and shows in the tournament read`() {
        val id = createTournament("Open House")

        setPublicVisibility(id, true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publicVisibility").value(true))
        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(jsonPath("$.publicVisibility").value(true))

        setPublicVisibility(id, false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publicVisibility").value(false))
        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(jsonPath("$.publicVisibility").value(false))
    }

    @Test
    fun `public visibility can be toggled while the tournament is active`() {
        val id = createTournament("Running")
        setStatus(id, "ACTIVE")

        setPublicVisibility(id, true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.publicVisibility").value(true))
    }

    @Test
    fun `a foreign admin cannot toggle another admin's public visibility`() {
        val id = createTournament("Alice's tournament")

        setPublicVisibility(id, true, otherAdminCookie)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(jsonPath("$.publicVisibility").value(false))
    }

    private fun setPublicVisibility(
        id: String,
        publicVisibility: Boolean,
        cookie: Cookie = sessionCookie,
    ) = mockMvc.perform(
        put("/api/tournaments/$id/public-visibility")
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"publicVisibility":$publicVisibility}"""),
    )

    private fun createTeam(tournamentId: String, name: String) {
        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to name,
                            "playerOneName" to "${name} 1",
                            "playerTwoName" to "${name} 2",
                        ),
                    ),
                ),
        )
            .andExpect(status().isCreated)
    }

    private fun setStatus(id: String, status: String) {
        jdbcTemplate.update("UPDATE tournaments SET status = ? WHERE id = ?", status, id)
    }

    private fun validUpdateBody(name: String): String = objectMapper.writeValueAsString(
        mapOf(
            "name" to name,
            "description" to "",
            "maxTeams" to 16,
            "settings" to mapOf("grandTichuBonus" to 200),
        ),
    )

    private fun createTournament(
        name: String,
        description: String = "",
        grandTichuBonus: Int = 200,
    ): String = createTournamentAs(sessionCookie, name, description, grandTichuBonus)

    private fun createTournamentAs(
        cookie: Cookie,
        name: String,
        description: String = "",
        grandTichuBonus: Int = 200,
    ): String {
        val body = mapOf(
            "name" to name,
            "description" to description,
            "maxTeams" to 16,
            "settings" to mapOf("grandTichuBonus" to grandTichuBonus),
        )
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        @Suppress("UNCHECKED_CAST")
        return (objectMapper.readValue(response, Map::class.java) as Map<String, Any>)["id"] as String
    }
}
