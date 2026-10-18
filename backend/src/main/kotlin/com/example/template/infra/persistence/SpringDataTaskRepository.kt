package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [TaskEntity]. It is an implementation detail of the
 * JPA adapter — never exposed outside `infra/persistence`. The application-facing
 * port is [com.example.template.domain.TaskRepository], implemented by
 * [JpaTaskRepository].
 */
interface SpringDataTaskRepository : JpaRepository<TaskEntity, String> {
    fun findAllByOwnerId(ownerId: String): List<TaskEntity>
}
