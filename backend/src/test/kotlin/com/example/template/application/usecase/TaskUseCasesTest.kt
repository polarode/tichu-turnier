package com.example.template.application.usecase

import com.example.template.application.api.CreateTask
import com.example.template.application.api.UpdateTask
import com.example.template.domain.AdminUserId
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
 *
 * The ownership rule is proven here, at the use-case seam: two admins (`owner` and
 * `intruder`) exercise every verb, and the intruder sees nothing.
 */
class TaskUseCasesTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val repository = InMemoryTaskRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "task-${++counter}"
    }

    private val owner = AdminUserId("admin-owner")
    private val intruder = AdminUserId("admin-intruder")

    private val createTask = CreateTaskCommand(repository, idGenerator, fixedClock)
    private val listTasks = ListTasksQuery(repository)
    private val getTask = GetTaskQuery(repository)
    private val updateTask = UpdateTaskCommand(repository, fixedClock)
    private val deleteTask = DeleteTaskCommand(repository)

    private fun create(title: String = "A", asAdmin: AdminUserId = owner): Task =
        createTask.create(CreateTask.CreateTaskRequest(title, ""), asAdmin)

    @Test
    fun `creates a task with TODO status, generated id and the caller as owner`() {
        val task = createTask.create(CreateTask.CreateTaskRequest("  Write docs  ", "  details  "), owner)

        assertEquals(TaskId("task-1"), task.id)
        assertEquals(owner, task.ownerId, "the creating admin is recorded as owner")
        assertEquals("Write docs", task.title, "title is trimmed")
        assertEquals("details", task.description, "description is trimmed")
        assertEquals(TaskStatus.TODO, task.status)
        assertEquals(fixedClock.instant(), task.createdAt)
        assertEquals(fixedClock.instant(), task.updatedAt)
    }

    @Test
    fun `rejects a blank title`() {
        assertFailsWith<InvalidInputException> {
            createTask.create(CreateTask.CreateTaskRequest("   ", "x"), owner)
        }
    }

    @Test
    fun `owner reads their own task`() {
        val created = create()

        assertEquals(created, getTask.get(created.id, owner))
    }

    @Test
    fun `owner updates their own task`() {
        val created = create("A")

        val updated = updateTask.update(
            UpdateTask.UpdateTaskRequest(created.id, "B", "b", TaskStatus.IN_PROGRESS),
            owner,
        )

        assertEquals("B", updated.title)
        assertEquals("b", updated.description)
        assertEquals(TaskStatus.IN_PROGRESS, updated.status)
        assertEquals(created.createdAt, updated.createdAt, "createdAt is preserved")
        assertEquals(owner, updated.ownerId, "ownership is preserved")
    }

    @Test
    fun `owner deletes their own task`() {
        val created = create("A")

        deleteTask.delete(created.id, owner)

        assertEquals(emptyList(), listTasks.list(owner))
    }

    @Test
    fun `a foreign admin cannot read another admin's task`() {
        val created = create("A")

        assertFailsWith<NotFoundException> { getTask.get(created.id, intruder) }
    }

    @Test
    fun `a foreign admin cannot update another admin's task`() {
        val created = create("A")

        assertFailsWith<NotFoundException> {
            updateTask.update(
                UpdateTask.UpdateTaskRequest(created.id, "hijacked", "", TaskStatus.DONE),
                intruder,
            )
        }
        assertEquals("A", getTask.get(created.id, owner).title, "the task is unchanged")
    }

    @Test
    fun `a foreign admin cannot delete another admin's task`() {
        val created = create("A")

        assertFailsWith<NotFoundException> { deleteTask.delete(created.id, intruder) }
        assertNotNull(repository.findById(created.id), "the task still exists")
    }

    @Test
    fun `listing returns only the caller's own tasks`() {
        val mine = create("mine")
        val alsoMine = create("also mine")
        val theirs = create("theirs", asAdmin = intruder)

        val ownerIds = listTasks.list(owner).map(Task::id).toSet()
        val intruderIds = listTasks.list(intruder).map(Task::id).toSet()

        assertEquals(setOf(mine.id, alsoMine.id), ownerIds)
        assertEquals(setOf(theirs.id), intruderIds)
    }

    @Test
    fun `getting a missing task fails`() {
        assertFailsWith<NotFoundException> { getTask.get(TaskId("nope"), owner) }
    }

    @Test
    fun `updating a missing task fails`() {
        assertFailsWith<NotFoundException> {
            updateTask.update(UpdateTask.UpdateTaskRequest(TaskId("nope"), "B", "", TaskStatus.DONE), owner)
        }
    }

    @Test
    fun `deleting a missing task fails`() {
        assertFailsWith<NotFoundException> { deleteTask.delete(TaskId("nope"), owner) }
    }
}
