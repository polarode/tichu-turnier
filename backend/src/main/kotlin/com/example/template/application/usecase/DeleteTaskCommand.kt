package com.example.template.application.usecase

import com.example.template.application.api.DeleteTask
import com.example.template.domain.TaskId
import com.example.template.domain.TaskRepository
import com.example.template.vocabulary.NotFoundException

class DeleteTaskCommand(
    private val repository: TaskRepository,
) : DeleteTask {
    override fun delete(id: TaskId) {
        repository.findById(id) ?: throw NotFoundException("Task ${id.value} not found")
        repository.delete(id)
    }
}
