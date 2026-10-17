package com.example.template.application.api

import com.example.template.domain.Task
import com.example.template.domain.TaskId

interface GetTask {
    fun get(id: TaskId): Task
}
