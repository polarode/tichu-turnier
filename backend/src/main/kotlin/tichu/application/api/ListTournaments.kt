package tichu.application.api

import tichu.domain.AdminUserId

interface ListTournaments {
    /**
     * Lists the tournaments [caller] organizes: those they lead ([OrganizerRole.LEAD]) and
     * those they co-organize ([OrganizerRole.CO]), newest first. Another admin's never appear.
     */
    fun list(caller: AdminUserId): List<OrganizedTournament>
}
