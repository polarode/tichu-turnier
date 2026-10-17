package com.example.template.infra.persistence

import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Driven adapter implementing the [TaskRepository] port with an in-memory store.
 *
 * Swap this for a JPA/MongoDB/JDBC adapter without touching the domain or use
 * cases — they depend only on the port. This is a plain class; it is turned into
 * a bean by [PersistenceConfiguration].
 */
class InMemoryTaskRepository : TaskRepository {

    private val store = ConcurrentHashMap<String, Task>()

    override fun save(task: Task): Task {
        store[task.id.value] = task
        return task
    }

    override fun findById(id: TaskId): Task? = store[id.value]

    override fun findAll(): List<Task> = store.values.toList()

    override fun delete(id: TaskId) {
        store.remove(id.value)
    }
}
