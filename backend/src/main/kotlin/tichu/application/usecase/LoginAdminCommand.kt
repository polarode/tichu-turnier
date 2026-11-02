package tichu.application.usecase

import tichu.application.api.IssuedSession
import tichu.application.api.LoginAdmin
import tichu.domain.AdminStatus
import tichu.domain.AdminUserRepository
import tichu.domain.PasswordHasher
import tichu.domain.normalizeEmail
import tichu.vocabulary.AccountDisabledException
import tichu.vocabulary.AccountPendingException
import tichu.vocabulary.InvalidCredentialsException
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
