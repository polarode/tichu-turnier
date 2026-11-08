package tichu.application.usecase

import tichu.application.api.TournamentWriteRequest
import tichu.application.api.UpdateTournament
import tichu.domain.AdminUserId
import tichu.domain.TeamRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.MaxTeamsBelowTeamCountException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Replaces the editable configuration of an existing tournament. A plain class wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Order of checks is deliberate: ownership first (a foreign id stays invisible as a
 * 404 regardless of body), then the status machine (409), then field validation (400),
 * then the capacity rule — lowering `max_teams` below the teams already registered is
 * rejected so an edit cannot orphan teams outside the configured limit (409).
 */
@Transactional
class UpdateTournamentCommand(
    private val repository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
    private val clock: Clock,
) : UpdateTournament {

    override fun update(
        id: TournamentId,
        command: TournamentWriteRequest,
        owner: AdminUserId,
    ): Tournament {
        val tournament = tournamentAccess.organizedOrThrow(id, owner)
        tournament.requireSetupPhase()

        val name = command.name.trim()
        TournamentInputValidator.validate(name, command.maxTeams, command.settings)

        val teamCount = teamRepository.countByTournament(id)
        if (command.maxTeams < teamCount) {
            throw MaxTeamsBelowTeamCountException(
                "maxTeams",
                "Tournament already has $teamCount teams; max teams cannot be lowered below that",
            )
        }

        return repository.save(
            tournament.copy(
                name = name,
                description = command.description.trim(),
                maxTeams = command.maxTeams,
                settings = command.settings,
                updatedAt = clock.instant(),
            ),
        )
    }
}
