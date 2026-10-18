package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Task
import com.example.template.domain.TaskId

interface GetTask {
    /** Fails as not-found when [id] does not exist or is owned by another admin. */
    fun get(id: TaskId, owner: AdminUserId): Task
}
