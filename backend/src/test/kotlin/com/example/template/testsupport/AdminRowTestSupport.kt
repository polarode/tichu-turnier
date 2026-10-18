package com.example.template.testsupport

import org.springframework.jdbc.core.JdbcTemplate

/**
 * Inserts a minimal admin row straight through JDBC, for persistence tests that only
 * need to satisfy tournaments' owner foreign key. Bypasses the adapter on purpose: the
 * row is fixture data, not the behaviour under test.
 */
fun insertAdminRow(
    jdbcTemplate: JdbcTemplate,
    id: String,
    email: String,
    status: String = "ACTIVE",
    role: String = "ADMIN",
) {
    jdbcTemplate.update(
        "INSERT INTO admin_users (id, email, password_hash, status, role, created_at) " +
            "VALUES (?, ?, 'irrelevant-hash', ?, ?, now()) ON CONFLICT DO NOTHING",
        id,
        email,
        status,
        role,
    )
}
