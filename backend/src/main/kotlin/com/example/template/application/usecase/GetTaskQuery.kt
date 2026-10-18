package com.example.template.application.usecase

import com.example.template.application.api.GetTask
import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository

class GetTaskQuery(
    private val repository: TaskRepository,
) : GetTask {
    override fun get(id: TaskId, owner: AdminUserId): Task =
        repository.findOwnedOrThrow(id, owner)
}
