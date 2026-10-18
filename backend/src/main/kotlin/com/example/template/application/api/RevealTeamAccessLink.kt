package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/**
 * Inbound port (driving side). Owner-only: shows the team's access link exactly
 * once. The raw token is minted fresh at reveal time and only its digest is
 * stored; a second reveal fails with `LINK_ALREADY_REVEALED` — rotation is the
 * documented recovery.
 */
interface RevealTeamAccessLink {
    fun reveal(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): RevealedTeamAccessLink
}

/** The one response in the API that carries raw team-link material. */
data class RevealedTeamAccessLink(
    val teamId: TeamId,
    val tournamentId: TournamentId,
    val token: String,
)