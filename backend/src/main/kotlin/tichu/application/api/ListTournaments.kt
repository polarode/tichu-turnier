package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament

interface ListTournaments {
    /** Lists only the tournaments owned by [owner]; another admin's never appear. */
    fun list(owner: AdminUserId): List<Tournament>
}
