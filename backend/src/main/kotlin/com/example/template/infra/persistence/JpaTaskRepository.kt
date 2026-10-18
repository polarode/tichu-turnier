package com.example.template.infra.persistence

import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository

/**
 * Driven adapter implementing the [TaskRepository] port on top of Spring Data JPA
 * and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean by
 * [PersistenceConfiguration]. Swapping the storage technology again means writing
 * a new adapter here and changing nothing above the port.
 */
class JpaTaskRepository(
    private val springDataTaskRepository: SpringDataTaskRepository,
) : TaskRepository {

    override fun save(task: Task): Task = springDataTaskRepository.save(TaskEntity.from(task)).toDomain()

    override fun findById(id: TaskId): Task? = springDataTaskRepository.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findAll(): List<Task> = springDataTaskRepository.findAll().map { it.toDomain() }

    override fun delete(id: TaskId) {
        springDataTaskRepository.deleteById(id.value)
    }
}
