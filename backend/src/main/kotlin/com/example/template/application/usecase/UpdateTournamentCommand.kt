package com.example.template.application.usecase

import com.example.template.application.api.TournamentWriteRequest
import com.example.template.application.api.UpdateTournament
import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentStatus
import com.example.template.vocabulary.TournamentNotEditableException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Replaces the editable configuration of an existing tournament. A plain class wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Order of checks is deliberate: ownership first (a foreign id stays invisible as a
 * 404 regardless of body), then the status machine (409), then field validation (400).
 */
@Transactional
class UpdateTournamentCommand(
    private val repository: TournamentRepository,
    private val clock: Clock,
) : UpdateTournament {

    override fun update(
        id: TournamentId,
        command: TournamentWriteRequest,
        owner: AdminUserId,
    ): Tournament {
        val tournament = repository.findOwnedOrThrow(id, owner)
        if (tournament.status != TournamentStatus.SETUP) {
            throw TournamentNotEditableException(
                "Tournament ${id.value} can only be edited while in setup " +
                    "(current status: ${tournament.status})",
            )
        }

        val name = command.name.trim()
        TournamentInputValidator.validate(name, command.maxTeams)

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
