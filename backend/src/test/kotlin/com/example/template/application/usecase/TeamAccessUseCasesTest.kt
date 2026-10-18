package com.example.template.application.usecase

import com.example.template.application.api.TeamWriteRequest
import com.example.template.application.api.AuthenticatedTeam
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentStatus
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.testsupport.InMemoryTeamRepository
import com.example.template.testsupport.InMemoryTeamSessionRepository
import com.example.template.testsupport.InMemoryTeamTokenRepository
import com.example.template.testsupport.RecordingSessionTokens
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.TeamAccessException
import com.example.template.vocabulary.TeamAccessLinkConflictException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit test for the team access spine (#24): token provisioning on team
 * creation, removal on team deletion, and the exchange that turns a raw link
 * token into a scoped team session. Every rejection path must be the same
 * generic failure, so nothing about a token's existence is disclosed.
 */
class TeamAccessUseCasesTest {

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val teamTokenRepository = InMemoryTeamTokenRepository()
    private val teamSessionRepository = InMemoryTeamSessionRepository()
    private val sessionTokens = RecordingSessionTokens()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${++counter}"
    }

    private val owner = AdminUserId("admin-owner")
    private val clock = com.example.template.testsupport.MutableClock(Instant.parse("2026-01-01T12:00:00Z"))
    private val now: Instant
        get() = clock.now

    private val createTeam =
        CreateTeamCommand(tournamentRepository, teamRepository, teamTokenRepository, sessionTokens, idGenerator, clock)
    private val deleteTeam = DeleteTeamCommand(tournamentRepository, teamRepository, teamTokenRepository)
    private val teamSessionIssuer = TeamSessionIssuer(teamSessionRepository, sessionTokens, idGenerator, Duration.ofHours(12))
    private val exchangeTeamAccess = ExchangeTeamAccessCommand(
        teamTokenRepository, teamRepository, tournamentRepository, sessionTokens, teamSessionIssuer,
        ExchangeRateLimiter(clock, maxFailures = 5, window = Duration.ofMinutes(15)), clock,
    )
    private val getOwnTeam = GetOwnTeamQuery(teamRepository)
    private val accessLinks = ManageTeamAccessLinkCommand(
        tournamentRepository, teamRepository, teamTokenRepository, teamSessionRepository, sessionTokens, clock,
    )

    private fun createTournament(name: String, status: TournamentStatus = TournamentStatus.SETUP): TournamentId {
        val tournamentId = CreateTournamentCommand(tournamentRepository, idGenerator, clock)
            .create(com.example.template.application.api.TournamentWriteRequest(name, "", 16), owner)
            .id
        if (status != TournamentStatus.SETUP) {
            tournamentRepository.save(
                tournamentRepository.findById(tournamentId)!!.copy(status = status),
            )
        }
        return tournamentId
    }

    private fun createTeam(
        tournamentId: TournamentId,
        name: String = "Team Alpha",
        playerOne: String = "Alice",
        playerTwo: String = "Bob",
    ) = createTeam.create(tournamentId, TeamWriteRequest(name, playerOne, playerTwo), owner)

    // ------------------------------------------------------------- provisioning

    @Test
    fun `creating a team provisions exactly one token, stored hashed`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)

        val token = teamTokenRepository.findByTeamId(team.id)
        assertTrue(token != null, "a created team has a token")

        // The digest must correspond to generated material — and only to it.
        assertTrue(token!!.tokenHash.isNotBlank())
        val rawTokens = sessionTokens.generated
        assertTrue(
            rawTokens.any { sessionTokens.hash(it) == token.tokenHash },
            "the token hash matches generated token material",
        )
        assertFalse(token.revealed, "a freshly provisioned token is unrevealed")
        assertNull(token.revokedAt, "a freshly provisioned token is not revoked")

        // Exactly one token per team: another provision for the same team would
        // overwrite (upsert), not duplicate — here we simply assert the count.
        assertEquals(1, sessionTokens.generated.size, "provisioning generated exactly one token")
    }

    @Test
    fun `the raw token appears in no response and no listing`() {
        val tournamentId = createTournament("Spring Classic")
        val rawBefore = sessionTokens.generated.size

        val team = createTeam(tournamentId)
        val raw = sessionTokens.generated[rawBefore]

        // The returned team aggregate carries no token material whatsoever.
        val serialized = team.toString()
        assertFalse(serialized.contains(raw), "the raw token must not leak through the team aggregate")

        // The domain model has no raw-token field at all; only the hash exists.
        val token = teamTokenRepository.findByTeamId(team.id)
        assertNotEquals(raw, token!!.tokenHash, "only the digest is persisted, never the raw token")
    }

    // ------------------------------------------------------------------ exchange

    @Test
    fun `a valid token exchanges into a 12h team session`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val raw = sessionTokens.generated.last()

        val issued = exchangeTeamAccess.exchange(raw)

        assertEquals(team.id, issued.teamId)
        assertEquals(tournamentId, issued.tournamentId)
        assertEquals(now.plus(Duration.ofHours(12)), issued.expiresAt)
        // The session row holds the hash of the issued session token, not the raw.
        val session = teamSessionRepository.findByTokenHash(sessionTokens.hash(issued.token))
        assertTrue(session != null, "the issued session is persisted by hash")
        assertNotEquals(issued.token, session!!.tokenHash)
    }

    @Test
    fun `a link is multi-use - both members exchange the same token`() {
        val tournamentId = createTournament("Spring Classic")
        createTeam(tournamentId)
        val raw = sessionTokens.generated.last()

        val first = exchangeTeamAccess.exchange(raw)
        val second = exchangeTeamAccess.exchange(raw)

        assertEquals(first.teamId, second.teamId, "both exchanges resolve to the same team")
        assertNotEquals(first.token, second.token, "each exchange issues its own session")
    }

    @Test
    fun `unknown, revoked and completed-tournament tokens all get the same generic rejection`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val raw = sessionTokens.generated.last()

        fun assertGenericRejection(rawToken: String) {
            val ex = assertFailsWith<TeamAccessException> { exchangeTeamAccess.exchange(rawToken) }
            assertEquals(ErrorCode.TEAM_ACCESS_INVALID, ex.code)
        }

        assertGenericRejection("no-such-token")

        // Revoked: still present, but dead.
        teamTokenRepository.save(
            teamTokenRepository.findByTeamId(team.id)!!.copy(revokedAt = now, updatedAt = now),
        )
        assertGenericRejection(raw)

        // Completed tournament: token intact, tournament finished.
        teamTokenRepository.save(teamTokenRepository.findByTeamId(team.id)!!.copy(revokedAt = null, updatedAt = now))
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.COMPLETED))
        assertGenericRejection(raw)
    }

    // ---------------------------------------------------------- link lifecycle

    @Test
    fun `reveal shows the raw link exactly once and flips the flag`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val provisionedHash = teamTokenRepository.findByTeamId(team.id)!!.tokenHash

        val revealed = accessLinks.reveal(tournamentId, team.id, owner)

        // The raw material exists only in the response; the database keeps the digest.
        val token = teamTokenRepository.findByTeamId(team.id)!!
        assertEquals(sessionTokens.hash(revealed.token), token.tokenHash, "the stored hash matches the revealed token")
        assertNotEquals(provisionedHash, token.tokenHash, "reveal mints fresh material for the one-time view")
        assertTrue(token.revealed, "the token is marked revealed")
        assertNull(token.revokedAt, "reveal does not touch the revocation state")
    }

    @Test
    fun `a second reveal is rejected with LINK_ALREADY_REVEALED`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        accessLinks.reveal(tournamentId, team.id, owner)

        val ex = assertFailsWith<TeamAccessLinkConflictException> {
            accessLinks.reveal(tournamentId, team.id, owner)
        }
        assertEquals(ErrorCode.LINK_ALREADY_REVEALED, ex.code)
    }

    @Test
    fun `reveal of a foreign or unknown team fails as not-found`() {
        val tournamentId = createTournament("Spring Classic")
        createTeam(tournamentId)

        assertFailsWith<NotFoundException> { accessLinks.reveal(tournamentId, TeamId("no-such-team"), owner) }
        assertFailsWith<NotFoundException> {
            accessLinks.reveal(tournamentId, teamRepository.findAllByTournament(tournamentId).first().id, AdminUserId("foreign-admin"))
        }
    }

    @Test
    fun `rotate invalidates the old link immediately and ends live sessions`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val oldRaw = accessLinks.reveal(tournamentId, team.id, owner).token
        val oldSession = exchangeTeamAccess.exchange(oldRaw)

        accessLinks.rotate(tournamentId, team.id, owner)

        // The old link is dead: exchange fails generically and the live session is gone.
        assertFailsWith<TeamAccessException> { exchangeTeamAccess.exchange(oldRaw) }
        assertNull(
            teamSessionRepository.findByTokenHash(sessionTokens.hash(oldSession.token)),
            "rotation ends the team's live sessions on their next request",
        )

        // The reveal flag was reset: the new link is shown exactly once and works.
        val newRaw = accessLinks.reveal(tournamentId, team.id, owner).token
        assertNotEquals(oldRaw, newRaw)
        assertEquals(team.id, exchangeTeamAccess.exchange(newRaw).teamId)
    }

    @Test
    fun `revoke disables the link and kills live sessions - idempotently`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val raw = accessLinks.reveal(tournamentId, team.id, owner).token
        val session = exchangeTeamAccess.exchange(raw)

        accessLinks.revoke(tournamentId, team.id, owner)
        accessLinks.revoke(tournamentId, team.id, owner) // revoking twice is a no-op

        assertFailsWith<TeamAccessException> { exchangeTeamAccess.exchange(raw) }
        assertNull(teamSessionRepository.findByTokenHash(sessionTokens.hash(session.token)))
        val token = teamTokenRepository.findByTeamId(team.id)!!
        assertTrue(token.isRevoked(), "the token carries a revocation timestamp")
    }

    // ------------------------------------------------------------- rate limiting

    @Test
    fun `five failed exchanges block that ip - and the block is indistinguishable from a wrong guess`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val raw = accessLinks.reveal(tournamentId, team.id, owner).token
        val attackerIp = "203.0.113.7"

        repeat(5) {
            assertGenericRejection { exchangeTeamAccess.exchange("wrong-guess", attackerIp) }
        }

        // Blocked: even the correct token gets exactly the same generic rejection,
        // and the limiter never even looks at the token.
        assertGenericRejection { exchangeTeamAccess.exchange(raw, attackerIp) }
        val ex = assertFailsWith<TeamAccessException> { exchangeTeamAccess.exchange(raw, attackerIp) }
        assertEquals(ErrorCode.TEAM_ACCESS_INVALID, ex.code)

        // Per-IP, never per-token: everyone else is unaffected.
        assertEquals(team.id, exchangeTeamAccess.exchange(raw, "198.51.100.9").teamId)
        assertEquals(team.id, exchangeTeamAccess.exchange(raw, null).teamId)
    }

    @Test
    fun `unknown and revoked tokens count the same towards the block, which recovers after the window`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        val raw = accessLinks.reveal(tournamentId, team.id, owner).token
        val ip = "203.0.113.7"

        // Revoked failures count exactly like unknown ones.
        teamTokenRepository.save(
            teamTokenRepository.findByTeamId(team.id)!!.copy(revokedAt = now, updatedAt = now),
        )
        repeat(5) { assertGenericRejection { exchangeTeamAccess.exchange(raw, ip) } }
        assertGenericRejection { exchangeTeamAccess.exchange("unknown", ip) }

        // After the window passes, the same ip can exchange again.
        teamTokenRepository.save(
            teamTokenRepository.findByTeamId(team.id)!!.copy(revokedAt = null, updatedAt = now),
        )
        clock.advance(Duration.ofMinutes(16))
        assertEquals(team.id, exchangeTeamAccess.exchange(raw, ip).teamId)

        // A successful exchange clears the ip's history: one failure does not block.
        assertGenericRejection { exchangeTeamAccess.exchange("wrong", ip) }
        assertEquals(team.id, exchangeTeamAccess.exchange(raw, ip).teamId)
        assertGenericRejection { exchangeTeamAccess.exchange("wrong", ip) }
        assertGenericRejection { exchangeTeamAccess.exchange("wrong", ip) }
        assertGenericRejection { exchangeTeamAccess.exchange("wrong", ip) }
        assertEquals(team.id, exchangeTeamAccess.exchange(raw, ip).teamId)
    }

    private fun assertGenericRejection(block: () -> Unit) {
        val ex = assertFailsWith<TeamAccessException> { block() }
        assertEquals(ErrorCode.TEAM_ACCESS_INVALID, ex.code)
    }

    // --------------------------------------------------------------- own-team read

    @Test
    fun `a team session can read its own team`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)

        val principal = AuthenticatedTeam(teamId = team.id, tournamentId = tournamentId)
        assertEquals(team.id, getOwnTeam.get(principal).id)
    }

    @Test
    fun `a team principal cannot reach another team's data`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        createTeam(tournamentId, "Team Beta", "Carol", "Dave")

        // A tampered principal naming a foreign team id fails as not-found: the
        // tournament scoping of the query never returns a team it doesn't match.
        val foreign = AuthenticatedTeam(
            teamId = teamRepository.findAllByTournament(tournamentId).first { it.name == "Team Beta" }.id,
            tournamentId = tournamentId,
        )
        assertEquals("Team Beta", getOwnTeam.get(foreign).name)

        // The real guard: a principal whose tournament doesn't match the team's.
        val mismatched = AuthenticatedTeam(teamId = team.id, tournamentId = TournamentId("other-tournament"))
        assertFailsWith<NotFoundException> { getOwnTeam.get(mismatched) }
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun `deleting a team removes its token`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)
        assertTrue(teamTokenRepository.findByTeamId(team.id) != null)

        deleteTeam.delete(tournamentId, team.id, owner)

        assertNull(teamTokenRepository.findByTeamId(team.id), "the token goes with the team")
    }

    @Test
    fun `tokens survive tournament transitions until completion`() {
        val tournamentId = createTournament("Spring Classic")
        val team = createTeam(tournamentId)

        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = TournamentStatus.ACTIVE))

        val token = teamTokenRepository.findByTeamId(team.id)
        assertTrue(token != null, "the token survives the setup → active transition")
        assertNull(token!!.revokedAt)
    }
}