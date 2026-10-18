package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskStatus

interface UpdateTask {
    /** Fails as not-found when the task is missing or owned by another admin. */
    fun update(command: UpdateTaskRequest, owner: AdminUserId): Task

    data class UpdateTaskRequest(
        val id: TaskId,
        val title: String,
        val description: String,
        val status: TaskStatus,
    )
}
