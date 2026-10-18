package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface CreateTask {
    /**
     * Creates a task owned by [owner] — the authenticated admin, supplied by the
     * controller from the session, never from the request body.
     */
    fun create(command: CreateTaskRequest, owner: AdminUserId): Task

    data class CreateTaskRequest(
        val title: String,
        val description: String,
    )
}
