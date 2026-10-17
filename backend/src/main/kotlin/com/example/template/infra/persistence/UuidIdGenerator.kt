package com.example.template.infra.persistence

import com.example.template.domain.IdGenerator
import com.example.template.domain.TaskId
import java.util.UUID

/** Driven adapter for the [IdGenerator] port, backed by random UUIDs. */
class UuidIdGenerator : IdGenerator {
    override fun newId(): TaskId = TaskId(UUID.randomUUID().toString())
}
