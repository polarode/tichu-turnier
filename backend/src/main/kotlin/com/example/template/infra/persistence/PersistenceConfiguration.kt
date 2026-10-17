package com.example.template.infra.persistence

import com.example.template.domain.IdGenerator
import com.example.template.domain.TaskRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PersistenceConfiguration {

    @Bean
    fun taskRepository(): TaskRepository = InMemoryTaskRepository()

    @Bean
    fun idGenerator(): IdGenerator = UuidIdGenerator()
}
