package com.example.template.infra.persistence

import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.vocabulary.DuplicatePlayerNameException
import com.example.template.vocabulary.DuplicateTeamNameException
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException

/**
 * Driven adapter implementing the [TeamRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean
 * by [PersistenceConfiguration].
 *
 * The team aggregate spans two tables: [save] writes the team row and both player
 * rows in the caller's transaction (the use case is `@Transactional`).
 */
class JpaTeamRepository(
    private val springDataTeamRepository: SpringDataTeamRepository,
    private val springDataPlayerRepository: SpringDataPlayerRepository,
) : TeamRepository {

    override fun save(team: Team): Team {
        try {
            val savedTeam = springDataTeamRepository.save(TeamEntity.from(team))
            // Replace semantics: an update must rename/replace the two players, never
            // leave the previous rows behind. The delete runs before the inserts in
            // the caller's transaction, so a failed save rolls the whole thing back.
            springDataPlayerRepository.deleteAllByTeamId(team.id.value)
            val savedPlayers = team.players.map { springDataPlayerRepository.save(PlayerEntity.from(it)) }
            return savedTeam.toDomain(savedPlayers)
        } catch (ex: DataIntegrityViolationException) {
            throw translateDuplicate(ex, team)
        }
    }

    /**
     * The use case checks uniqueness before saving, but a concurrent create can still
     * slip through and hit the database's unique indexes. That race surfaces as a
     * [DataIntegrityViolationException]; it is translated into the same typed,
     * field-anchored errors the pre-save checks throw, so callers never see a 500
     * for a duplicate. Violations the indexes cannot explain are rethrown.
     */
    private fun translateDuplicate(ex: DataIntegrityViolationException, team: Team): RuntimeException {
        val detail = postgresDetail(ex)
        return when {
            detail.contains("uq_teams_tournament_name") -> DuplicateTeamNameException(
                "name",
                "Team name \"${team.name}\" is already taken in this tournament",
            )
            detail.contains("uq_players_tournament_name") -> {
                // The Postgres detail line carries the conflicting lower(name); match it
                // back to the input field. Falls back to playerOneName if unparseable.
                val duplicated = PLAYER_NAME_DETAIL.find(detail)?.groupValues?.get(2)
                    ?: team.players.first().name.lowercase()
                val field = when (duplicated) {
                    team.players.getOrNull(1)?.name?.lowercase() -> "playerTwoName"
                    else -> "playerOneName"
                }
                DuplicatePlayerNameException(field, "Player \"$duplicated\" is already on a team in this tournament")
            }
            else -> throw ex
        }
    }

    /**
     * The constraint name lives in the error message and the conflicting key in the
     * detail — `PSQLException.getMessage()` does not always carry the detail, so both
     * are read from the server error message and joined.
     */
    private fun postgresDetail(ex: DataIntegrityViolationException): String {
        var cause: Throwable? = ex
        while (cause != null) {
            if (cause is PSQLException) {
                val serverError = cause.serverErrorMessage
                return listOfNotNull(serverError?.message, serverError?.detail).joinToString("\n")
            }
            cause = cause.cause
        }
        return ex.mostSpecificCause.message.orEmpty()
    }

    override fun findAllByTournament(tournamentId: TournamentId): List<Team> {
        val teams = springDataTeamRepository.findAllByTournamentId(tournamentId.value)
        return withPlayers(teams)
    }

    override fun countByTournament(tournamentId: TournamentId): Int =
        springDataTeamRepository.countByTournamentId(tournamentId.value)

    override fun findById(teamId: TeamId, tournamentId: TournamentId): Team? =
        springDataTeamRepository.findByIdAndTournamentId(teamId.value, tournamentId.value)
            ?.let { withPlayers(listOf(it)) }
            ?.firstOrNull()

    override fun findById(teamId: TeamId): Team? =
        springDataTeamRepository.findById(teamId.value).orElse(null)
            ?.let { withPlayers(listOf(it)) }
            ?.firstOrNull()

    override fun delete(teamId: TeamId, tournamentId: TournamentId) {
        springDataTeamRepository.deleteByIdAndTournamentId(teamId.value, tournamentId.value)
    }

    /** Postgres detail format: `Key (tournament_id, lower(name))=(<uuid>, <name>) already exists.` */
    private companion object {
        // Postgres prints expression-index columns with their type, e.g. `lower(name::text)`.
        val PLAYER_NAME_DETAIL = Regex("\\(tournament_id, lower\\(name(::text)?\\)\\)=\\([^,]+, (.*)\\) already exists\\.")
    }

    /** Loads the players of each team and reassembles the domain aggregates. */
    private fun withPlayers(teams: List<TeamEntity>): List<Team> {
        val teamIds = teams.map { it.id }
        val playersByTeam = springDataPlayerRepository.findAllByTeamIdIn(teamIds).groupBy { it.teamId }
        return teams.map { team -> team.toDomain(playersByTeam[team.id].orEmpty()) }
    }
}
