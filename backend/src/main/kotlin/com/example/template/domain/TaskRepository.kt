package com.example.template.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 */
interface TaskRepository {

    fun save(task: Task): Task

    fun findById(id: TaskId): Task?

    /** Only the caller's tasks — the ownership scope is pushed into the query. */
    fun findAllByOwner(owner: AdminUserId): List<Task>

    /**
     * Deletes by id without an ownership check. Only call it after verifying the
     * caller owns the task — use [com.example.template.application.api.DeleteTask]
     * rather than the port directly.
     */
    fun delete(id: TaskId)
}
