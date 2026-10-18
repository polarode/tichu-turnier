package com.example.template.application.api

import com.example.template.domain.AdminUserId

/**
 * Inbound port (driving side). Owner-only: approves a `PENDING` registration request,
 * activating it and recording who approved it and when.
 */
interface ApproveAdmin {
    fun approve(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView
}
