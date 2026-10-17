package com.example.template.infra.rest.web

import com.example.template.domain.Task
import com.example.template.domain.TaskStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Wire representations. The REST layer owns these; the domain never sees them.
 * Mapping domain -> response happens via [TaskResponse.from].
 */
data class TaskResponse(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(task: Task): TaskResponse = TaskResponse(
            id = task.id.value,
            title = task.title,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}

data class CreateTaskRequestBody(
    @field:NotBlank val title: String,
    val description: String = "",
)

data class UpdateTaskRequestBody(
    @field:NotBlank val title: String,
    val description: String = "",
    val status: TaskStatus,
)
