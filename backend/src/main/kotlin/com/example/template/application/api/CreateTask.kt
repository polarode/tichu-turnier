package com.example.template.application.api

import com.example.template.domain.Task

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface CreateTask {
    fun create(command: CreateTaskRequest): Task

    data class CreateTaskRequest(
        val title: String,
        val description: String,
    )
}
