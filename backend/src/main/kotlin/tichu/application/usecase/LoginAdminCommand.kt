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
import tichu.vocabulary.TooManyAttemptsException
import java.time.Clock

/**
 * Verifies credentials and issues a fresh session. Unknown email and wrong password
 * deliberately produce the same failure so callers cannot probe which accounts exist.
 * Only once the credentials match does the account's lifecycle matter: a pending or
 * disabled account gets its own stable code, distinct from invalid credentials.
 *
 * Failed logins are counted per source IP by their own [FailedAttemptLimiter]. A
 * blocked source is refused before the password is checked, with its own code rather
 * than the invalid-credentials one, so an organizer knows to wait instead of doubting
 * their password. Only invalid credentials count as failures; a correct password on a
 * pending or disabled account does not. A successful login clears the source's history. A request without a [LoginAdmin.LoginAdminRequest.sourceIp] is never limited.
 */
class LoginAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionIssuer: AdminSessionIssuer,
    private val rateLimiter: FailedAttemptLimiter,
    private val clock: Clock,
) : LoginAdmin {

    override fun login(command: LoginAdmin.LoginAdminRequest): IssuedSession {
        val sourceIp = command.sourceIp
        if (sourceIp != null && rateLimiter.isBlocked(sourceIp)) {
            throw TooManyAttemptsException("Too many failed login attempts, try again later")
        }
        val email = normalizeEmail(command.email)
        val user = adminUsers.findByEmail(email)
        if (user == null || !passwordHasher.matches(command.password, user.passwordHash)) {
            sourceIp?.let { rateLimiter.registerFailure(it) }
            throw InvalidCredentialsException("Invalid email or password")
        }
        return when (user.status) {
            AdminStatus.ACTIVE -> {
                sourceIp?.let { rateLimiter.registerSuccess(it) }
                sessionIssuer.issue(user, clock.instant())
            }
            AdminStatus.PENDING -> throw AccountPendingException("Account is awaiting approval")
            AdminStatus.DISABLED -> throw AccountDisabledException("Account has been disabled")
        }
    }
}
