package com.example.template.application.usecase

import com.example.template.application.api.DeleteTask
import com.example.template.domain.AdminUserId
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository

class DeleteTaskCommand(
    private val repository: TaskRepository,
) : DeleteTask {
    override fun delete(id: TaskId, owner: AdminUserId) {
        repository.findOwnedOrThrow(id, owner)
        repository.delete(id)
    }
}
