package com.example.template.application.usecase

import com.example.template.application.api.GetTask
import com.example.template.domain.Task
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import com.example.template.vocabulary.NotFoundException

class GetTaskQuery(
    private val repository: TaskRepository,
) : GetTask {
    override fun get(id: TaskId): Task =
        repository.findById(id) ?: throw NotFoundException("Task ${id.value} not found")
}
