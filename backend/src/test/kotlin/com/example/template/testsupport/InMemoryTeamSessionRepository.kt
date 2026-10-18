package com.example.template.testsupport

import com.example.template.domain.TeamId
import com.example.template.domain.TeamSession
import com.example.template.domain.TeamSessionRepository
import java.util.concurrent.ConcurrentHashMap

/** In-memory double for [TeamSessionRepository], used by pure use-case tests. */
class InMemoryTeamSessionRepository : TeamSessionRepository {

    private val store = ConcurrentHashMap<String, TeamSession>()

    override fun save(session: TeamSession): TeamSession {
        store[session.id.value] = session
        return session
    }

    override fun findByTokenHash(tokenHash: String): TeamSession? =
        store.values.firstOrNull { it.tokenHash == tokenHash }

    override fun deleteByTokenHash(tokenHash: String) {
        store.values.removeIf { it.tokenHash == tokenHash }
    }

    override fun deleteByTeamId(teamId: TeamId) {
        store.values.removeIf { it.teamId == teamId }
    }
}