package tichu.application.api

import tichu.domain.AdminUserId

interface ListTournaments {
    /**
     * Lists the tournaments [owner] organizes: those they lead ([OrganizerRole.LEAD]) and
     * those they co-organize ([OrganizerRole.CO]), newest first. Another admin's never appear.
     */
    fun list(owner: AdminUserId): List<OrganizedTournament>
}
