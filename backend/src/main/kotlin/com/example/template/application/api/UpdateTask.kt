package com.example.template.application.api

import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskStatus

interface UpdateTask {
    fun update(command: UpdateTaskRequest): Task

    data class UpdateTaskRequest(
        val id: TaskId,
        val title: String,
        val description: String,
        val status: TaskStatus,
    )
}
