package com.example.template.infra.persistence

import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [Task]. Lives in the infra layer — the domain entity stays
 * framework-free; this is the wire/storage format of the JPA adapter.
 *
 * Mutable fields with private setters: Hibernate needs setters (or field access)
 * to hydrate, while the rest of the app can only go through the domain entity.
 */
@Entity
@Table(name = "tasks")
class TaskEntity(
    @Id
    val id: String,
    title: String,
    description: String,
    status: TaskStatus,
    createdAt: Instant,
    updatedAt: Instant,
) {
    var title: String = title
        private set

    var description: String = description
        private set

    @Enumerated(EnumType.STRING)
    var status: TaskStatus = status
        private set

    var createdAt: Instant = createdAt
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun toDomain(): Task = Task(
        id = TaskId(id),
        title = title,
        description = description,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(task: Task): TaskEntity = TaskEntity(
            id = task.id.value,
            title = task.title,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
