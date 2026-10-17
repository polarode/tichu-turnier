package com.example.template.application.api

import com.example.template.domain.TaskId

interface DeleteTask {
    fun delete(id: TaskId)
}
