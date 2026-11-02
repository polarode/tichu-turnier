package tichu.application.usecase

import tichu.domain.Table
import tichu.domain.TeamId
import tichu.vocabulary.ForbiddenException

/**
 * The single place the seat rule is applied: only a team seated at a table may
 * read its games, record them, or confirm and retract there. Shared so the
 * team-session verbs cannot drift apart on who gets a 403.
 */
internal fun Table.requireSeated(teamId: TeamId) {
    if (!seats(teamId)) {
        throw ForbiddenException("Team ${teamId.value} is not seated at table ${id.value}")
    }
}
