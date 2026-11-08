package tichu.domain

import java.time.Instant

/**
 * An admin the lead organizer added to a tournament to help run it. Framework-free: a
 * plain pairing of tournament and admin, with the moment the access was granted. The
 * lead organizer is the tournament's `ownerId` and is never a co-organizer row.
 */
data class CoOrganizer(
    val tournamentId: TournamentId,
    val adminUserId: AdminUserId,
    val addedAt: Instant,
)

/**
 * Outbound port: persistence for [CoOrganizer]. Implemented in `infra/persistence`.
 * Co-organizers are granted and revoked per tournament; access takes effect as soon as
 * the row exists, without any acceptance step.
 */
interface CoOrganizerRepository {

    fun add(coOrganizer: CoOrganizer)

    /** Ends the co-organizer access of [adminUserId] on [tournamentId]. A no-op when they hold none. */
    fun remove(tournamentId: TournamentId, adminUserId: AdminUserId)

    /** Whether [adminUserId] is a co-organizer of [tournamentId]. Says nothing about the lead. */
    fun isCoOrganizer(tournamentId: TournamentId, adminUserId: AdminUserId): Boolean

    /** The co-organizers of [tournamentId], oldest [CoOrganizer.addedAt] first. */
    fun findByTournament(tournamentId: TournamentId): List<CoOrganizer>

    /** The tournaments [adminUserId] co-organizes. Says nothing about the ones they lead. */
    fun findTournamentIdsByAdmin(adminUserId: AdminUserId): List<TournamentId>
}
