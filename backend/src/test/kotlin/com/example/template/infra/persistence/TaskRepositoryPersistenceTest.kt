package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import com.example.template.domain.TaskStatus
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-port integration test: the [TaskRepository] adapter must run against
 * real PostgreSQL (Testcontainers). Deliberately does NOT use test transactions —
 * each repository call must commit, so the tests prove data actually reaches the
 * database and survives beyond a single repository instance (i.e. a restart).
 */
@SpringBootTest(classes = [Application::class])
class TaskRepositoryPersistenceTest(
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val springDataTaskRepository: SpringDataTaskRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val owner = AdminUserId("admin-owner")
    private val otherOwner = AdminUserId("admin-other")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tasks")
        // tasks.owner_id is a real FK, so the owners must exist before saving.
        insertAdmin(owner.value, "owner@example.com")
        insertAdmin(otherOwner.value, "other@example.com")
    }

    private fun insertAdmin(id: String, email: String) {
        jdbcTemplate.update(
            "INSERT INTO admin_users (id, email, password_hash, created_at) VALUES (?, ?, ?, now()) ON CONFLICT DO NOTHING",
            id,
            email,
            "irrelevant-hash",
        )
    }

    @Test
    fun `saved task is read back with all fields intact including its owner`() {
        val task = task(id = "t-1", title = "Write docs", description = "for the template")

        val stored = taskRepository.save(task)
        val loaded = taskRepository.findById(TaskId("t-1"))

        assertEquals(task, stored)
        assertEquals(task, loaded)
        assertEquals(owner, loaded?.ownerId)
    }

    @Test
    fun `data lives in the database, not in a repository cache`() {
        taskRepository.save(task(id = "t-1", title = "Survives"))

        // A brand-new adapter instance over the same EntityManager must find the
        // row — proving it was committed to Postgres, not held in memory. This is
        // what makes the data survive a backend restart.
        val freshAdapter = JpaTaskRepository(springDataTaskRepository)
        val loaded = freshAdapter.findById(TaskId("t-1"))

        assertEquals("Survives", loaded?.title)

        val rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Long::class.java)
        assertEquals(1L, rowCount)
    }

    @Test
    fun `findAllByOwner returns only that owner's tasks`() {
        taskRepository.save(task(id = "t-1", title = "A", ownerId = owner))
        taskRepository.save(task(id = "t-2", title = "B", ownerId = owner))
        taskRepository.save(task(id = "t-3", title = "C", ownerId = otherOwner))

        val mine = taskRepository.findAllByOwner(owner)

        assertEquals(2, mine.size)
        assertTrue(mine.all { it.ownerId == owner })
        assertFalse(mine.any { it.id.value == "t-3" })
    }

    @Test
    fun `delete removes the task`() {
        taskRepository.save(task(id = "t-1", title = "A"))

        taskRepository.delete(TaskId("t-1"))

        assertNull(taskRepository.findById(TaskId("t-1")))
        assertFalse(taskRepository.findAllByOwner(owner).any { it.id.value == "t-1" })
    }

    @Test
    fun `save on an existing id updates the row instead of duplicating it`() {
        taskRepository.save(task(id = "t-1", title = "Before"))
        taskRepository.save(task(id = "t-1", title = "After"))

        val mine = taskRepository.findAllByOwner(owner)

        assertEquals(1, mine.size)
        assertEquals("After", mine.single().title)
    }

    private fun task(
        id: String,
        title: String,
        description: String = "",
        status: TaskStatus = TaskStatus.TODO,
        ownerId: AdminUserId = owner,
    ): Task = Task(
        id = TaskId(id),
        ownerId = ownerId,
        title = title,
        description = description,
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
