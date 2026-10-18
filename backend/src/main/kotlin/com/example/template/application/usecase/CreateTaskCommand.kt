package com.example.template.application.usecase

import com.example.template.application.api.CreateTask
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import com.example.template.domain.TaskStatus
import com.example.template.vocabulary.InvalidInputException
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 */
class CreateTaskCommand(
    private val repository: TaskRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : CreateTask {

    override fun create(command: CreateTask.CreateTaskRequest, owner: AdminUserId): Task {
        if (command.title.isBlank()) {
            throw InvalidInputException("Task title must not be blank")
        }
        val now = clock.instant()
        val task = Task(
            id = TaskId(idGenerator.newId()),
            ownerId = owner,
            title = command.title.trim(),
            description = command.description.trim(),
            status = TaskStatus.TODO,
            createdAt = now,
            updatedAt = now,
        )
        return repository.save(task)
    }
}
