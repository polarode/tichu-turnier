package com.example.template.application.usecase

import com.example.template.application.api.ListTasks
import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskRepository

class ListTasksQuery(
    private val repository: TaskRepository,
) : ListTasks {
    override fun list(owner: AdminUserId): List<Task> =
        repository.findAllByOwner(owner).sortedByDescending { it.createdAt }
}
