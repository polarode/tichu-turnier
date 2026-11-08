package tichu.infra.rest

import tichu.domain.AdminStatus
import tichu.domain.AdminUserRepository
import tichu.domain.PasswordHasher
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.seedAdmin
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * Seam: the co-organizer HTTP surface (#131). The lead organizer adds an active admin by
 * email; the co-organizer then runs the tournament, follows its live stream and sees it on
 * the dashboard, but cannot delete it or manage the organizers. Strangers still get 404.
 */
@SpringBootTest(classes = [tichu.main.Application::class])
class OrganizersIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var leadEmail: String
    private lateinit var leadCookie: Cookie
    private lateinit var coEmail: String
    private lateinit var coCookie: Cookie
    private lateinit var strangerCookie: Cookie

    @BeforeEach
    fun authenticate() {
        leadEmail = uniqueEmail("lead")
        seedAdmin(adminUsers, passwordHasher, leadEmail)
        leadCookie = mockMvc.loginAndCaptureSession(objectMapper, leadEmail)

        coEmail = uniqueEmail("co")
        seedAdmin(adminUsers, passwordHasher, coEmail)
        coCookie = mockMvc.loginAndCaptureSession(objectMapper, coEmail)

        strangerCookie = loginNewAdmin(uniqueEmail("stranger"), AdminStatus.ACTIVE)
    }

    @Test
    fun `the lead adds an active admin and both organizers see the same list`() {
        val id = createTournament(leadCookie, "Shared Cup")

        mockMvc.perform(
            post("/api/tournaments/$id/organizers").cookie(leadCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$coEmail"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.lead.email").value(leadEmail))
            .andExpect(jsonPath("$.coOrganizers.length()").value(1))
            .andExpect(jsonPath("$.coOrganizers[0].email").value(coEmail))
            .andExpect(jsonPath("$.coOrganizers[0].addedAt").isNotEmpty)

        for (cookie in listOf(leadCookie, coCookie)) {
            mockMvc.perform(get("/api/tournaments/$id/organizers").cookie(cookie))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.lead.email").value(leadEmail))
                .andExpect(jsonPath("$.coOrganizers.length()").value(1))
                .andExpect(jsonPath("$.coOrganizers[0].email").value(coEmail))
        }
    }

    @Test
    fun `unknown, pending and disabled emails are refused with one identical field-anchored 400`() {
        val id = createTournament(leadCookie, "Shared Cup")
        val pendingEmail = uniqueEmail("pending")
        seedAdmin(adminUsers, passwordHasher, pendingEmail, status = AdminStatus.PENDING)
        val disabledEmail = uniqueEmail("disabled")
        seedAdmin(adminUsers, passwordHasher, disabledEmail, status = AdminStatus.DISABLED)

        val details = listOf(uniqueEmail("unknown"), pendingEmail, disabledEmail).map { email ->
            val result = postOrganizer(leadCookie, id, email)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_ADMIN_WITH_EMAIL"))
                .andExpect(jsonPath("$.field").value("email"))
                .andReturn()
            objectMapper.readTree(result.response.contentAsString)["detail"].asText()
        }

        assertEquals(1, details.toSet().size, "the refusal must not reveal which case it was: $details")
    }

    @Test
    fun `adding yourself as co-organizer is refused with a field-anchored 400`() {
        val id = createTournament(leadCookie, "Shared Cup")

        postOrganizer(leadCookie, id, leadEmail)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CANNOT_ADD_SELF_AS_CO_ORGANIZER"))
            .andExpect(jsonPath("$.field").value("email"))
    }

    @Test
    fun `adding an existing co-organizer again is a field-anchored 409`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        postOrganizer(leadCookie, id, coEmail)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_CO_ORGANIZER"))
            .andExpect(jsonPath("$.field").value("email"))
    }

    @Test
    fun `a co-organizer cannot add an organizer and gets 403`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)
        val thirdEmail = uniqueEmail("third")
        seedAdmin(adminUsers, passwordHasher, thirdEmail)

        postOrganizer(coCookie, id, thirdEmail)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `a co-organizer cannot delete the tournament and it still exists`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(delete("/api/tournaments/$id").cookie(coCookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        mockMvc.perform(get("/api/tournaments/$id").cookie(leadCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Shared Cup"))
    }

    @Test
    fun `a stranger gets 404 when reading the organizers`() {
        val id = createTournament(leadCookie, "Shared Cup")

        mockMvc.perform(get("/api/tournaments/$id/organizers").cookie(strangerCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `the dashboard lists a co-organized tournament with the CO role and the lead's email`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(get("/api/tournaments").cookie(coCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].organizerRole").value("CO"))
            .andExpect(jsonPath("$[0].leadOrganizerEmail").value(leadEmail))
    }

    @Test
    fun `the lead sees the tournament with the LEAD role and its own email`() {
        val id = createTournament(leadCookie, "Shared Cup")

        mockMvc.perform(get("/api/tournaments/$id").cookie(leadCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.organizerRole").value("LEAD"))
            .andExpect(jsonPath("$.leadOrganizerEmail").value(leadEmail))
    }

    @Test
    fun `a co-organizer reads the tournament with the CO role`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(get("/api/tournaments/$id").cookie(coCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.organizerRole").value("CO"))
            .andExpect(jsonPath("$.leadOrganizerEmail").value(leadEmail))
    }

    @Test
    fun `a co-organizer updates the tournament in setup`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(
            put("/api/tournaments/$id").cookie(coCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed by co","description":"","maxTeams":16}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed by co"))
            .andExpect(jsonPath("$.organizerRole").value("CO"))
            .andExpect(jsonPath("$.leadOrganizerEmail").value(leadEmail))
    }

    @Test
    fun `a co-organizer sets public visibility`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(
            put("/api/tournaments/$id/public-visibility").cookie(coCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"publicVisibility":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publicVisibility").value(true))
    }

    @Test
    fun `a co-organizer opens the tournament's live event stream`() {
        val id = createTournament(leadCookie, "Shared Cup")
        postOrganizer(leadCookie, id, coEmail).andExpect(status().isCreated)

        mockMvc.perform(get("/api/tournaments/$id/events").cookie(coCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/event-stream")))
    }

    private fun postOrganizer(cookie: Cookie, tournamentId: String, email: String) = mockMvc.perform(
        post("/api/tournaments/$tournamentId/organizers").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("email" to email))),
    )

    private fun createTournament(cookie: Cookie, name: String): String {
        val response: MvcResult = mockMvc.perform(
            post("/api/tournaments").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","description":"","maxTeams":16}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(response.response.contentAsString)["id"].asText()
    }

    private fun loginNewAdmin(email: String, status: AdminStatus): Cookie {
        seedAdmin(adminUsers, passwordHasher, email, status = status)
        return mockMvc.loginAndCaptureSession(objectMapper, email)
    }

    private fun uniqueEmail(prefix: String) = "$prefix-${UUID.randomUUID()}@example.com"
}
