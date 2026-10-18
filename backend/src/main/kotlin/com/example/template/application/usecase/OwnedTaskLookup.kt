package com.example.template.application.usecase

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import com.example.template.vocabulary.NotFoundException

/**
 * The single place the ownership rule is applied on a single resource: load the task
 * and fail exactly as if it did not exist when another admin owns it. Returning
 * not-found (rather than forbidden) means a foreign admin cannot even learn that the
 * id exists. Shared so read, update and delete cannot drift apart.
 */
internal fun TaskRepository.findOwnedOrThrow(id: TaskId, owner: AdminUserId): Task {
    val task = findById(id)
    if (task == null || !task.isOwnedBy(owner)) {
        throw NotFoundException("Task ${id.value} not found")
    }
    return task
}
