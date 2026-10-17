package com.example.template.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 */
interface TaskRepository {

    fun save(task: Task): Task

    fun findById(id: TaskId): Task?

    fun findAll(): List<Task>

    fun delete(id: TaskId)
}
