package com.example.template.application.usecase

import com.example.template.application.api.UpdateTask
import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskRepository
import com.example.template.vocabulary.InvalidInputException
import java.time.Clock

class UpdateTaskCommand(
    private val repository: TaskRepository,
    private val clock: Clock,
) : UpdateTask {

    override fun update(command: UpdateTask.UpdateTaskRequest, owner: AdminUserId): Task {
        if (command.title.isBlank()) {
            throw InvalidInputException("Task title must not be blank")
        }
        val existing = repository.findOwnedOrThrow(command.id, owner)

        val now = clock.instant()
        val updated = existing
            .rename(command.title.trim(), command.description.trim(), now)
            .withStatus(command.status, now)
        return repository.save(updated)
    }
}
