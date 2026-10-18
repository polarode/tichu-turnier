package com.example.template.application.usecase

import com.example.template.application.api.RegisterAdmin
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.PasswordHasher
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 */
class RegisterAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val passwordHasher: PasswordHasher,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : RegisterAdmin {

    override fun register(command: RegisterAdmin.RegisterAdminRequest): AdminUser {
        val email = command.email.trim().lowercase()
        if (adminUsers.findByEmail(email) != null) {
            throw EmailAlreadyRegisteredException("An account with this email already exists")
        }

        val user = AdminUser(
            id = AdminUserId(idGenerator.newId()),
            email = email,
            passwordHash = passwordHasher.hash(command.password),
            status = AdminStatus.PENDING,
            role = AdminRole.ADMIN,
            createdAt = clock.instant(),
        )
        return adminUsers.save(user)
    }
}
