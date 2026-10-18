package com.example.template.testsupport

import com.example.template.domain.TeamId
import com.example.template.domain.TeamToken
import com.example.template.domain.TeamTokenRepository
import java.util.concurrent.ConcurrentHashMap

/** In-memory double for [TeamTokenRepository], used by pure use-case tests. */
class InMemoryTeamTokenRepository : TeamTokenRepository {

    private val store = ConcurrentHashMap<String, TeamToken>()

    override fun save(token: TeamToken): TeamToken {
        store[token.teamId.value] = token
        return token
    }

    override fun findByTeamId(teamId: TeamId): TeamToken? = store[teamId.value]

    override fun findByTokenHash(tokenHash: String): TeamToken? =
        store.values.firstOrNull { it.tokenHash == tokenHash }

    override fun deleteByTeamId(teamId: TeamId) {
        store.remove(teamId.value)
    }
}