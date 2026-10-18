package com.example.template.domain

import java.time.Instant

/**
 * The core domain entity. Framework-free: no Spring, no persistence annotations.
 *
 * [ownerId] is the admin who created it: every read/write is scoped to that owner,
 * so a resource can never be loaded or changed by another admin's session.
 *
 * Domain behavior lives here as pure functions that return a new instance rather
 * than mutating in place, so the entity stays easy to reason about and test.
 */
data class Task(
    val id: TaskId,
    val ownerId: AdminUserId,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isOwnedBy(owner: AdminUserId): Boolean = ownerId == owner

    fun rename(newTitle: String, newDescription: String, now: Instant): Task =
        copy(title = newTitle, description = newDescription, updatedAt = now)

    fun withStatus(newStatus: TaskStatus, now: Instant): Task =
        copy(status = newStatus, updatedAt = now)
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class TaskId(val value: String)

enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
}
