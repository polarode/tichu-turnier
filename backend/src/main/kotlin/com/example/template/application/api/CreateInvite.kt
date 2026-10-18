package com.example.template.application.api

/**
 * Inbound port (driving side). Owner-only: issues a single-use, expiring invite for an
 * email and returns the raw token exactly once. The implementation enforces the
 * requester's role, so a non-owner admin is refused with a 403 `FORBIDDEN` problem.
 */
interface CreateInvite {

    fun create(requester: AuthenticatedAdmin, request: CreateInviteRequest): IssuedInvite

    data class CreateInviteRequest(val email: String)
}
