package com.example.template.infra.rest

import com.example.template.application.api.CreateTask
import com.example.template.application.api.DeleteTask
import com.example.template.application.api.GetTask
import com.example.template.application.api.ListTasks
import com.example.template.application.api.UpdateTask
import com.example.template.infra.rest.web.TaskController
import com.example.template.infra.rest.web.WebControllerAdvice
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Wires the web adapters as beans. Controllers receive inbound ports as
 * constructor args, resolved from the beans declared in UseCaseConfiguration.
 */
@Configuration
class RestConfiguration(
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {

    @Bean
    fun taskController(
        createTask: CreateTask,
        listTasks: ListTasks,
        getTask: GetTask,
        updateTask: UpdateTask,
        deleteTask: DeleteTask,
    ): TaskController = TaskController(createTask, listTasks, getTask, updateTask, deleteTask)

    @Bean
    fun webControllerAdvice(): WebControllerAdvice = WebControllerAdvice()

    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/api/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        }
    }
}
