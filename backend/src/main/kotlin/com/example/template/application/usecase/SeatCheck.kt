package com.example.template.application.usecase

import com.example.template.domain.Table
import com.example.template.domain.TeamId
import com.example.template.vocabulary.ForbiddenException

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
