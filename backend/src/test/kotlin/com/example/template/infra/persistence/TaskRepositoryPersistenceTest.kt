package com.example.template.infra.persistence

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

    @BeforeEach
    fun clearTable() {
        jdbcTemplate.update("DELETE FROM tasks")
    }

    @Test
    fun `saved task is read back with all fields intact`() {
        val task = task(id = "t-1", title = "Write docs", description = "for the template")

        val stored = taskRepository.save(task)
        val loaded = taskRepository.findById(TaskId("t-1"))

        assertEquals(task, stored)
        assertEquals(task, loaded)
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
    fun `findAll returns every saved task`() {
        taskRepository.save(task(id = "t-1", title = "A"))
        taskRepository.save(task(id = "t-2", title = "B"))

        val all = taskRepository.findAll()

        assertEquals(2, all.size)
        assertTrue(all.any { it.title == "A" })
        assertTrue(all.any { it.title == "B" })
    }

    @Test
    fun `delete removes the task`() {
        taskRepository.save(task(id = "t-1", title = "A"))

        taskRepository.delete(TaskId("t-1"))

        assertNull(taskRepository.findById(TaskId("t-1")))
        assertFalse(taskRepository.findAll().any { it.id.value == "t-1" })
    }

    @Test
    fun `save on an existing id updates the row instead of duplicating it`() {
        taskRepository.save(task(id = "t-1", title = "Before"))
        taskRepository.save(task(id = "t-1", title = "After"))

        val all = taskRepository.findAll()

        assertEquals(1, all.size)
        assertEquals("After", all.single().title)
    }

    private fun task(
        id: String,
        title: String,
        description: String = "",
        status: TaskStatus = TaskStatus.TODO,
    ): Task = Task(
        id = TaskId(id),
        title = title,
        description = description,
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
