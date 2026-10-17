package com.example.template.application.usecase

import com.example.template.application.api.CreateTask
import com.example.template.application.api.DeleteTask
import com.example.template.application.api.GetTask
import com.example.template.application.api.ListTasks
import com.example.template.application.api.UpdateTask
import com.example.template.domain.IdGenerator
import com.example.template.domain.TaskRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * The only place use cases are turned into beans. They remain plain classes;
 * their dependencies (ports) are injected here as constructor arguments, which
 * Spring resolves from other beans. No @Service / @Autowired anywhere.
 */
@Configuration
class UseCaseConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun createTask(
        repository: TaskRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateTask = CreateTaskCommand(repository, idGenerator, clock)

    @Bean
    fun listTasks(repository: TaskRepository): ListTasks = ListTasksQuery(repository)

    @Bean
    fun getTask(repository: TaskRepository): GetTask = GetTaskQuery(repository)

    @Bean
    fun updateTask(repository: TaskRepository, clock: Clock): UpdateTask =
        UpdateTaskCommand(repository, clock)

    @Bean
    fun deleteTask(repository: TaskRepository): DeleteTask = DeleteTaskCommand(repository)
}
