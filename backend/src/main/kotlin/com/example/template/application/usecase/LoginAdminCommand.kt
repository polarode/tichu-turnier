package com.example.template.application.usecase

import com.example.template.application.api.IssuedSession
import com.example.template.application.api.LoginAdmin
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.domain.normalizeEmail
import com.example.template.vocabulary.AccountDisabledException
import com.example.template.vocabulary.AccountPendingException
import com.example.template.vocabulary.InvalidCredentialsException
import java.time.Clock

/**
 * Verifies credentials and issues a fresh session. Unknown email and wrong password
 * deliberately produce the same failure so callers cannot probe which accounts exist.
 * Only once the credentials match does the account's lifecycle matter: a pending or
 * disabled account gets its own stable code, distinct from invalid credentials.
 */
class LoginAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionIssuer: AdminSessionIssuer,
    private val clock: Clock,
) : LoginAdmin {

    override fun login(command: LoginAdmin.LoginAdminRequest): IssuedSession {
        val email = normalizeEmail(command.email)
        val user = adminUsers.findByEmail(email)
        if (user == null || !passwordHasher.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException("Invalid email or password")
        }
        return when (user.status) {
            AdminStatus.ACTIVE -> sessionIssuer.issue(user, clock.instant())
            AdminStatus.PENDING -> throw AccountPendingException("Account is awaiting approval")
            AdminStatus.DISABLED -> throw AccountDisabledException("Account has been disabled")
        }
    }
}
