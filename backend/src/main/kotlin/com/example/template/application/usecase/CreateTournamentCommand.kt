package com.example.template.application.usecase

import com.example.template.application.api.CreateTournament
import com.example.template.application.api.TournamentWriteRequest
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentStatus
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 */
class CreateTournamentCommand(
    private val repository: TournamentRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : CreateTournament {

    override fun create(command: TournamentWriteRequest, owner: AdminUserId): Tournament {
        val name = command.name.trim()
        TournamentInputValidator.validate(name, command.maxTeams)

        val now = clock.instant()
        val tournament = Tournament(
            id = TournamentId(idGenerator.newId()),
            ownerId = owner,
            name = name,
            description = command.description.trim(),
            maxTeams = command.maxTeams,
            settings = command.settings,
            // Every new tournament enters the status machine at its first state.
            status = TournamentStatus.SETUP,
            createdAt = now,
            updatedAt = now,
        )
        return repository.save(tournament)
    }
}
