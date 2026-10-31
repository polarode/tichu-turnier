package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Game
import com.example.template.domain.GameKey
import com.example.template.domain.GameParticipant
import com.example.template.domain.GameRepository
import com.example.template.domain.PlayerId
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TableId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TichuCall
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.domain.TournamentRepository
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.insertAdminRow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.template.vocabulary.GameAlreadyExistsException

/**
 * Repository-port integration test: the [GameRepository] adapter must run against
 * real PostgreSQL (Testcontainers). Deliberately does NOT use test transactions —
 * each repository call must commit, so the tests prove data actually reaches the
 * database and survives beyond a single repository instance. Where the port
 * demands a caller transaction (`MANDATORY` propagation), the test wraps calls
 * in its own [TransactionTemplate], mirroring the `@Transactional` use cases.
 */
@SpringBootTest(classes = [Application::class])
class GameRepositoryPersistenceTest(
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val owner = AdminUserId("admin-owner")

    private fun gameKey() = com.example.template.domain.GameKey(tournamentId, 1, 1, 1)
    private val tournamentId = TournamentId("t-1")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        insertAdminRow(jdbcTemplate, owner.value, "[EMAIL]")
        seedTournamentAndTeams()
    }

    // ---- fixtures ------------------------------------------------------------

    private fun seedTournamentAndTeams() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = owner,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        seedTeam("team-a", "Team Alpha", "Alice", "Bob")
        seedTeam("team-b", "Team Beta", "Carol", "Dave")
    }

    private fun seedTeam(id: String, name: String, playerOne: String, playerTwo: String) {
        val team = teamRepository.save(
            Team(
                id = TeamId(id),
                tournamentId = tournamentId,
                name = name,
                players = listOf(
                    com.example.template.domain.Player(
                        id = PlayerId("$id-p1"),
                        tournamentId = tournamentId,
                        teamId = TeamId(id),
                        name = playerOne,
                    ),
                    com.example.template.domain.Player(
                        id = PlayerId("$id-p2"),
                        tournamentId = tournamentId,
                        teamId = TeamId(id),
                        name = playerTwo,
                    ),
                ),
            ),
        )
        assertEquals(id, team.id.value)
    }

    private fun round(number: Int = 1, locked: Boolean = false) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = com.example.template.domain.Pairing(
            tables = listOf(Table(TableId(1), TeamId("team-a"), TeamId("team-b"))),
        ),
        locked = locked,
    )

    private fun game(
        gameNumber: Int = 1,
        roundNumber: Int = 1,
        tableNumber: Int = 1,
        version: Int = 1,
        base1: Int = 55,
        base2: Int = 45,
        beschiss: Boolean = false,
    ) = Game(
        id = null,
        tournamentId = tournamentId,
        roundNumber = roundNumber,
        tableNumber = tableNumber,
        gameNumber = gameNumber,
        team1 = TeamId("team-a"),
        team2 = TeamId("team-b"),
        baseScoreTeam1 = base1,
        baseScoreTeam2 = base2,
        beschiss = beschiss,
        version = version,
        participants = listOf(
            GameParticipant(PlayerId("team-a-p1"), TeamId("team-a"), 1, TichuCall.SMALL, 0),
            GameParticipant(PlayerId("team-a-p2"), TeamId("team-a"), 3, TichuCall.NONE, 1),
            GameParticipant(PlayerId("team-b-p1"), TeamId("team-b"), 2, TichuCall.NONE, 0),
            GameParticipant(PlayerId("team-b-p2"), TeamId("team-b"), 4, TichuCall.GRAND, 2),
        ),
    )

    private fun createInTransaction(game: Game): Game =
        transactionTemplate.execute { gameRepository.create(game) }!!

    private fun updateInTransaction(
        expectedVersion: Int,
        update: (Game) -> Game,
    ): Game? = transactionTemplate.execute {
        gameRepository.updateWithVersion(
            com.example.template.domain.GameKey(tournamentId, 1, 1, 1),
            expectedVersion,
            update,
        )
    }

    // ---- tests ---------------------------------------------------------------

    @Test
    fun `created game is read back by natural key with participants and version 1`() {
        val created = createInTransaction(game())

        assertTrue(created.id!! > 0, "surrogate id is assigned")
        assertEquals(1, created.version, "version starts at 1")
        assertEquals(4, created.participants.size)

        val loaded = gameRepository.find(gameKey())
        assertEquals(created.copy(id = loaded?.id), loaded)
        assertEquals(
            listOf(1, 3, 2, 4),
            loaded!!.participants.map { it.position }.zip(loaded.participants.map { it.teamId.value })
                .map { it.first },
            "sanity: positions preserved",
        )
    }

    @Test
    fun `create on an existing natural key throws GameAlreadyExists`() {
        createInTransaction(game())

        val exception = assertThrows(GameAlreadyExistsException::class.java) { createInTransaction(game(beschiss = true)) }

        assertIs<GameAlreadyExistsException>(exception)
    }

    @Test
    fun `lookup of an uncreated game returns null`() {
        assertNull(gameRepository.find(gameKey()))
        assertNull(gameRepository.find(com.example.template.domain.GameKey(tournamentId, 1, 1, 4)))
    }

    @Test
    fun `updateWithVersion applies facts, bumps version and replaces participants`() {
        createInTransaction(game())

        val updated = updateInTransaction(1) { current ->
            current.copy(
                baseScoreTeam1 = 70,
                baseScoreTeam2 = 30,
                beschiss = true,
                version = 99, // the caller's guess is irrelevant; the adapter owns versioning
                participants = current.participants.map {
                    it.copy(position = it.position?.plus(1), bombs = 0, call = TichuCall.NONE)
                },
            )
        }!!

        assertEquals(2, updated.version, "version bumps by exactly one")
        assertEquals(70, updated.baseScoreTeam1)
        assertTrue(updated.beschiss)
        assertEquals(listOf(0, 0, 0, 0), updated.participants.map { it.bombs })
        assertEquals(4, participantRowCount())
    }

    @Test
    fun `updateWithVersion with a stale version returns null and changes nothing`() {
        createInTransaction(game())

        val result = updateInTransaction(7) { it.copy(baseScoreTeam1 = 99, baseScoreTeam2 = 1) }

        assertNull(result, "stale writer loses")
        val stored = gameRepository.find(gameKey())!!
        assertEquals(1, stored.version)
        assertEquals(55, stored.baseScoreTeam1, "no partial write happened")
    }

    @Test
    fun `updateWithVersion on an uncreated game returns null`() {
        assertNull(updateInTransaction(1) { it.copy(baseScoreTeam1 = 60, baseScoreTeam2 = 40) })
    }

    @Test
    fun `first commit wins on a serialized version race`() {
        createInTransaction(game())

        // Two writers read version 1; the first commit bumps to 2, the second
        // writer's conditional update then matches 0 rows.
        val first = updateInTransaction(1) { it.copy(baseScoreTeam1 = 60, baseScoreTeam2 = 40) }
        val second = updateInTransaction(1) { it.copy(baseScoreTeam1 = 30, baseScoreTeam2 = 70) }

        assertTrue(first != null, "first commit wins")
        assertNull(second, "second commit is stale")
        assertEquals(60, gameRepository.find(gameKey())!!.baseScoreTeam1)
    }

    @Test
    fun `a failed participant insert leaves no orphaned game row (atomic submit)`() {
        // One participant references a player that does not exist: the FK
        // violation must roll back the game row too — no orphans either way.
        val ghost = game().copy(
            participants = game().participants.take(3) +
                GameParticipant(PlayerId("ghost-player"), TeamId("team-b"), 4, TichuCall.NONE, 0),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            transactionTemplate.execute { gameRepository.create(ghost) }
        }

        assertNull(gameRepository.find(gameKey()), "no game row survived the failed submit")
        assertEquals(0, gameRowCount())
        assertEquals(0, participantRowCount())
    }

    @Test
    fun `deleting the tournament cascades to its games and participants`() {
        createInTransaction(game())

        tournamentRepository.delete(tournamentId)

        assertNull(gameRepository.find(gameKey()))
        assertEquals(0, gameRowCount())
        assertEquals(0, participantRowCount())
    }

    private fun gameRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM games", Int::class.java)!!

    private fun participantRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM game_participants", Int::class.java)!!
}
