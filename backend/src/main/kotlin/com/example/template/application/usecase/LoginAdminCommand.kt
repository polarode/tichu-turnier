package com.example.template.application.usecase

import com.example.template.application.api.IssuedSession
import com.example.template.application.api.LoginAdmin
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.vocabulary.InvalidCredentialsException
import java.time.Clock

/**
 * Verifies credentials and issues a fresh session. Unknown email and wrong password
 * deliberately produce the same failure so callers cannot probe which accounts exist.
 */
class LoginAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionIssuer: AdminSessionIssuer,
    private val clock: Clock,
) : LoginAdmin {

    override fun login(command: LoginAdmin.LoginAdminRequest): IssuedSession {
        val email = command.email.trim().lowercase()
        val user = adminUsers.findByEmail(email)
        if (user == null || !passwordHasher.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException("Invalid email or password")
        }
        return sessionIssuer.issue(user, clock.instant())
    }
}
