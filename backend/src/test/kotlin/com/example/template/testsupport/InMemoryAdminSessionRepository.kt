package com.example.template.testsupport

import com.example.template.domain.AdminSession
import com.example.template.domain.AdminSessionRepository

/** In-memory double for [AdminSessionRepository], used by pure use-case tests. */
class InMemoryAdminSessionRepository : AdminSessionRepository {

    private val sessions = LinkedHashMap<String, AdminSession>()

    override fun save(session: AdminSession): AdminSession {
        sessions[session.id.value] = session
        return session
    }

    override fun findByTokenHash(tokenHash: String): AdminSession? =
        sessions.values.firstOrNull { it.tokenHash == tokenHash }

    override fun deleteByTokenHash(tokenHash: String) {
        sessions.values.removeIf { it.tokenHash == tokenHash }
    }
}
