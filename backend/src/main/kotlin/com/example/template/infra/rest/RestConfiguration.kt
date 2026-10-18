package com.example.template.infra.rest

import com.example.template.application.api.CreateTask
import com.example.template.application.api.DeleteTask
import com.example.template.application.api.GetTask
import com.example.template.application.api.ListTasks
import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.LogoutAdmin
import com.example.template.application.api.RefreshSession
import com.example.template.application.api.RegisterAdmin
import com.example.template.application.api.UpdateTask
import com.example.template.infra.rest.web.AuthController
import com.example.template.infra.rest.web.SessionCookieFactory
import com.example.template.infra.rest.web.TaskController
import com.example.template.infra.rest.web.WebControllerAdvice
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Wires the web adapters as beans. Controllers receive inbound ports as
 * constructor args, resolved from the beans declared in UseCaseConfiguration.
 */
@Configuration
class RestConfiguration(
    @param:Value("\${app.auth.session-cookie-name}") private val sessionCookieName: String,
    @param:Value("\${app.auth.session-cookie-secure}") private val sessionCookieSecure: Boolean,
    @param:Value("\${app.auth.session-ttl}") private val sessionTtl: Duration,
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
    fun sessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(sessionCookieName, sessionCookieSecure, sessionTtl)

    @Bean
    fun authController(
        registerAdmin: RegisterAdmin,
        loginAdmin: LoginAdmin,
        refreshSession: RefreshSession,
        logoutAdmin: LogoutAdmin,
        sessionCookie: SessionCookieFactory,
    ): AuthController =
        AuthController(registerAdmin, loginAdmin, refreshSession, logoutAdmin, sessionCookie)

    @Bean
    fun webControllerAdvice(): WebControllerAdvice = WebControllerAdvice()
}
