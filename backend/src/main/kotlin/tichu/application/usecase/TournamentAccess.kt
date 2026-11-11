package tichu.application.usecase

import tichu.application.api.OrganizerRole
import tichu.domain.AdminRole
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.CoOrganizerRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.NotFoundException

/**
 * The single place the organizer rule is applied on a single tournament. A caller who
 * does not organize the tournament fails exactly as if it did not exist, so a stranger
 * cannot even learn that the id exists. Returning not-found (rather than forbidden) is
 * the rule; [ledOrThrow] keeps it for strangers and adds the lead check on top.
 *
 * An organizer is the lead organizer (the tournament's `ownerId`) or one of its
 * co-organizers. Verbs that any organizer may run use [organizedOrThrow]. Lead-only
 * verbs (deleting the tournament, managing its co-organizers) use [ledOrThrow]. Shared
 * so every verb (edit, delete, start, …) cannot drift apart.
 *
 * The platform owner (an active [AdminRole.OWNER]) acts as lead organizer on every
 * tournament, so [roleOf] resolves it to [OrganizerRole.LEAD] everywhere.
 */
class TournamentAccess(
    private val tournamentRepository: TournamentRepository,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val adminUserRepository: AdminUserRepository,
) {

    /**
     * The tournament, if [admin] organizes it (lead or co-organizer). Otherwise not-found,
     * as for a missing id.
     */
    fun organizedOrThrow(id: TournamentId, admin: AdminUserId): Tournament =
        organizedWithRoleOrThrow(id, admin).tournament

    /**
     * The tournament together with [admin]'s role on it, if [admin] organizes it. Otherwise
     * not-found, as for a missing id.
     */
    fun organizedWithRoleOrThrow(id: TournamentId, admin: AdminUserId): RoleOnTournament {
        val tournament = tournamentRepository.findById(id)
        val role = tournament?.let { roleOf(it, admin) }
        if (tournament == null || role == null) {
            throw NotFoundException("Tournament ${id.value} not found")
        }
        return RoleOnTournament(tournament, role)
    }

    /**
     * [admin]'s role on [tournament]: [OrganizerRole.LEAD] for the lead organizer (the
     * `ownerId`) and for an active platform owner, [OrganizerRole.CO] for a co-organizer,
     * null for anyone else.
     */
    fun roleOf(tournament: Tournament, admin: AdminUserId): OrganizerRole? = when {
        tournament.isOwnedBy(admin) -> OrganizerRole.LEAD
        isActiveOwner(admin) -> OrganizerRole.LEAD
        coOrganizerRepository.isCoOrganizer(tournament.id, admin) -> OrganizerRole.CO
        else -> null
    }

    /** Same rule as session authentication: an owner counts only while its account is active. */
    private fun isActiveOwner(admin: AdminUserId): Boolean {
        val user = adminUserRepository.findById(admin) ?: return false
        return user.role == AdminRole.OWNER && user.isActive()
    }

    /**
     * The tournament, if [admin] is its lead organizer. Strangers get not-found; a
     * co-organizer gets forbidden. The lead check is [roleOf]'s, so there is one definition.
     */
    fun ledOrThrow(id: TournamentId, admin: AdminUserId): Tournament {
        val organized = organizedWithRoleOrThrow(id, admin)
        if (organized.role != OrganizerRole.LEAD) {
            throw ForbiddenException("Only the lead organizer may do this on tournament ${id.value}")
        }
        return organized.tournament
    }
}

/** A tournament together with the caller's [role] on it, as [TournamentAccess] resolves it. */
data class RoleOnTournament(
    val tournament: Tournament,
    val role: OrganizerRole,
)
