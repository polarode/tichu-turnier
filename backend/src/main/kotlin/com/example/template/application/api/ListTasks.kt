package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task

interface ListTasks {
    /** Lists only the tasks owned by [owner]; another admin's tasks never appear. */
    fun list(owner: AdminUserId): List<Task>
}
