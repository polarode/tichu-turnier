package tichu.application.api

import tichu.domain.Tournament

/**
 * A tournament as seen by one of its organizers: the tournament itself, the caller's
 * [role] on it, and the email of its lead organizer (the admin who created it). For a
 * lead the email is the caller's own (#131).
 */
data class OrganizedTournament(
    val tournament: Tournament,
    val role: OrganizerRole,
    val leadOrganizerEmail: String,
)

/** The caller's role on a tournament they organize. Lead-only verbs need [LEAD]. */
enum class OrganizerRole {
    LEAD,
    CO,
}
