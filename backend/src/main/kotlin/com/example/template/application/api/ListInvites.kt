package com.example.template.application.api

/**
 * Inbound port (driving side). Owner-only: lists issued invites for the console. The
 * view carries no token material, so a listing can never reproduce an issued link.
 */
interface ListInvites {
    fun list(requester: AuthenticatedAdmin): List<AdminInviteView>
}
