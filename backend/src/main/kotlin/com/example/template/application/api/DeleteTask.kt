package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TaskId

interface DeleteTask {
    /** Fails as not-found when the task is missing or owned by another admin. */
    fun delete(id: TaskId, owner: AdminUserId)
}
