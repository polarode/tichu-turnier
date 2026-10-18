package com.example.template.application.usecase

import com.example.template.application.api.BootstrapOwner
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.normalizeEmail
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired as a
 * bean in [UseCaseConfiguration] — never annotated with @Service — and run once at
 * startup by an `ApplicationRunner`.
 *
 * The owner's credentials come from configuration as a pre-computed password hash, so
 * the bootstrap never handles a plaintext secret. An existing account is only claimed
 * when it already has the `OWNER` role: since no endpoint can create or promote to
 * `OWNER`, such a row can only have come from this bootstrap, so converging its
 * credential from configuration is safe (that is how the owner password is rotated) and
 * its lifecycle state is left untouched. Any other account holding the configured email
 * — for instance a self-registered `PENDING` request — is somebody else's: it is never
 * promoted and never has its password overwritten. That conflict is logged rather than
 * thrown, so a squatter cannot stop the application from starting.
 */
class BootstrapOwnerCommand(
    private val adminUsers: AdminUserRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val ownerEmail: String,
    private val ownerPasswordHash: String,
) : BootstrapOwner {

    private val log = LoggerFactory.getLogger(BootstrapOwnerCommand::class.java)

    override fun ensureOwner(): AdminUser? {
        val email = normalizeEmail(ownerEmail)
        if (email.isEmpty() || ownerPasswordHash.isBlank()) {
            return null
        }

        val existing = adminUsers.findByEmail(email)
        if (existing != null) {
            if (existing.role != AdminRole.OWNER) {
                log.error(
                    "Refusing to claim {} as the platform owner: an account with that email exists without the " +
                        "OWNER role. Remove it or configure a different owner email.",
                    email,
                )
                return null
            }
            val converged = existing.copy(passwordHash = ownerPasswordHash, role = AdminRole.OWNER)
            return if (converged == existing) existing else adminUsers.save(converged)
        }

        val owner = AdminUser(
            id = AdminUserId(idGenerator.newId()),
            email = email,
            passwordHash = ownerPasswordHash,
            status = AdminStatus.ACTIVE,
            role = AdminRole.OWNER,
            createdAt = clock.instant(),
        )
        return adminUsers.save(owner)
    }
}
