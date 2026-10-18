package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [TaskRepository] port, kept as a fast fake for
 * unit tests (see `TaskUseCasesTest`). The production adapter is
 * [JpaTaskRepository] on PostgreSQL; swapping it never touches the domain or use
 * cases — they depend only on the port.
 */
class InMemoryTaskRepository : TaskRepository {

    private val store = ConcurrentHashMap<String, Task>()

    override fun save(task: Task): Task {
        store[task.id.value] = task
        return task
    }

    override fun findById(id: TaskId): Task? = store[id.value]

    override fun findAllByOwner(owner: AdminUserId): List<Task> =
        store.values.filter { it.isOwnedBy(owner) }

    override fun delete(id: TaskId) {
        store.remove(id.value)
    }
}
