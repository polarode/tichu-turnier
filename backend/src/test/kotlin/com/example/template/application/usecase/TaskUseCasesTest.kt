package com.example.template.application.usecase

import com.example.template.application.api.CreateTask
import com.example.template.application.api.UpdateTask
import com.example.template.domain.IdGenerator
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskStatus
import com.example.template.infra.persistence.InMemoryTaskRepository
import com.example.template.vocabulary.InvalidInputException
import com.example.template.vocabulary.NotFoundException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Pure unit test: no Spring context. The ports are satisfied with a real in-memory
 * repository and tiny fakes for the clock and id generator, so the use cases are
 * exercised in isolation and deterministically.
 */
class TaskUseCasesTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val repository = InMemoryTaskRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): TaskId = TaskId("task-${++counter}")
    }

    private val createTask = CreateTaskCommand(repository, idGenerator, fixedClock)
    private val listTasks = ListTasksQuery(repository)
    private val getTask = GetTaskQuery(repository)
    private val updateTask = UpdateTaskCommand(repository, fixedClock)
    private val deleteTask = DeleteTaskCommand(repository)

    @Test
    fun `creates a task with TODO status and generated id`() {
        val task = createTask.create(CreateTask.CreateTaskRequest("  Write docs  ", "  details  "))

        assertEquals(TaskId("task-1"), task.id)
        assertEquals("Write docs", task.title, "title is trimmed")
        assertEquals("details", task.description, "description is trimmed")
        assertEquals(TaskStatus.TODO, task.status)
        assertEquals(fixedClock.instant(), task.createdAt)
        assertEquals(fixedClock.instant(), task.updatedAt)
    }

    @Test
    fun `rejects a blank title`() {
        assertFailsWith<InvalidInputException> {
            createTask.create(CreateTask.CreateTaskRequest("   ", "x"))
        }
    }

    @Test
    fun `lists tasks newest first`() {
        val a = createTask.create(CreateTask.CreateTaskRequest("A", ""))
        val b = createTask.create(CreateTask.CreateTaskRequest("B", ""))
        // same timestamp (fixed clock), so insertion is a stable tiebreaker-independent set
        val ids = listTasks.list().map(Task::id).toSet()
        assertEquals(setOf(a.id, b.id), ids)
    }

    @Test
    fun `gets a task by id`() {
        val created = createTask.create(CreateTask.CreateTaskRequest("A", ""))
        assertEquals(created, getTask.get(created.id))
    }

    @Test
    fun `getting a missing task fails`() {
        assertFailsWith<NotFoundException> { getTask.get(TaskId("nope")) }
    }

    @Test
    fun `updates title, description and status`() {
        val created = createTask.create(CreateTask.CreateTaskRequest("A", "a"))

        val updated = updateTask.update(
            UpdateTask.UpdateTaskRequest(created.id, "B", "b", TaskStatus.IN_PROGRESS),
        )

        assertEquals("B", updated.title)
        assertEquals("b", updated.description)
        assertEquals(TaskStatus.IN_PROGRESS, updated.status)
        assertEquals(created.createdAt, updated.createdAt, "createdAt is preserved")
    }

    @Test
    fun `updating a missing task fails`() {
        assertFailsWith<NotFoundException> {
            updateTask.update(UpdateTask.UpdateTaskRequest(TaskId("nope"), "B", "", TaskStatus.DONE))
        }
    }

    @Test
    fun `deletes a task`() {
        val created = createTask.create(CreateTask.CreateTaskRequest("A", ""))
        deleteTask.delete(created.id)
        assertEquals(emptyList(), listTasks.list())
    }

    @Test
    fun `deleting a missing task fails`() {
        assertFailsWith<NotFoundException> { deleteTask.delete(TaskId("nope")) }
    }

    @Test
    fun `smoke - repository returns saved task`() {
        val created = createTask.create(CreateTask.CreateTaskRequest("A", ""))
        assertNotNull(repository.findById(created.id))
    }
}
