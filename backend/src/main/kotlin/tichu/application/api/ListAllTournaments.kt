package tichu.application.api

/**
 * Inbound port (driving side). Owner-only: the implementation enforces the requester's
 * role, so a non-owner admin is refused with a 403 `FORBIDDEN` problem rather than
 * reaching any data.
 */
interface ListAllTournaments {
    /**
     * Lists every tournament on the platform, each with its lead organizer's email and the
     * LEAD role, newest first. The owner acts as lead organizer everywhere.
     */
    fun listAll(requester: AuthenticatedAdmin): List<OrganizedTournament>
}
