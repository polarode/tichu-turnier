package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.CreateTask
import com.example.template.application.api.DeleteTask
import com.example.template.application.api.GetTask
import com.example.template.application.api.ListTasks
import com.example.template.application.api.UpdateTask
import com.example.template.domain.TaskId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter. Depends only on the inbound-port interfaces, which are injected
 * by [com.example.template.infra.rest.RestConfiguration]. No @Autowired; this is a
 * plain class constructed as a bean.
 *
 * It is annotated with @RestController/@RequestMapping only because those are the
 * web framework's contract for routing — not for dependency wiring.
 *
 * Every method takes the authenticated admin from the security context and passes
 * that id to the use case; the owner is never read from the request, so a caller
 * cannot claim another admin's data.
 */
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val createTask: CreateTask,
    private val listTasks: ListTasks,
    private val getTask: GetTask,
    private val updateTask: UpdateTask,
    private val deleteTask: DeleteTask,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: AuthenticatedAdmin): List<TaskResponse> =
        listTasks.list(principal.adminUserId).map(TaskResponse::from)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TaskResponse = TaskResponse.from(getTask.get(TaskId(id), principal.adminUserId))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody body: CreateTaskRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TaskResponse {
        val task = createTask.create(
            CreateTask.CreateTaskRequest(title = body.title, description = body.description),
            principal.adminUserId,
        )
        return TaskResponse.from(task)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateTaskRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TaskResponse {
        val task = updateTask.update(
            UpdateTask.UpdateTaskRequest(
                id = TaskId(id),
                title = body.title,
                description = body.description,
                status = body.status,
            ),
            principal.adminUserId,
        )
        return TaskResponse.from(task)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): ResponseEntity<Void> {
        deleteTask.delete(TaskId(id), principal.adminUserId)
        return ResponseEntity.noContent().build()
    }
}
