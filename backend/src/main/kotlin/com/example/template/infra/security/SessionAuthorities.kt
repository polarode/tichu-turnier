package com.example.template.infra.security

/**
 * Authorities used to separate the two session worlds. The security chain decides
 * by authority, not by principal type, so a team session can never pass an admin
 * endpoint and vice versa — the rejection happens in the filter chain, before any
 * controller or use case runs.
 */
const val ADMIN_SESSION_AUTHORITY = "ROLE_ADMIN_SESSION"
const val TEAM_SESSION_AUTHORITY = "ROLE_TEAM_SESSION"