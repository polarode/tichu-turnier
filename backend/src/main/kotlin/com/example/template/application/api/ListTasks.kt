package com.example.template.application.api

import com.example.template.domain.Task

interface ListTasks {
    fun list(): List<Task>
}
