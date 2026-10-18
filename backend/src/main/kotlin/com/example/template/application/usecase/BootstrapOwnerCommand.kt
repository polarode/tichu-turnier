package com.example.template.application.usecase

import com.example.template.application.api.BootstrapOwner
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.normalizeEmail
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired as a
 * bean in [UseCaseConfiguration] — never annotated with @Service — and run once at
 * startup by an `ApplicationRunner`.
 *
 * The owner's credentials come from configuration as a pre-computed password hash, so
 * the bootstrap never handles a plaintext secret. An existing account is only claimed
 * as the owner when its stored hash equals the configured one — that is, it was
 * provisioned from this same configuration. An account holding the configured email but
 * a different credential is a conflict the bootstrap refuses to resolve by takeover, so
 * a self-registered account can never be promoted to owner. Its lifecycle state is left
 * alone, keeping re-runs idempotent.
 */
class BootstrapOwnerCommand(
    private val adminUsers: AdminUserRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val ownerEmail: String,
    private val ownerPasswordHash: String,
) : BootstrapOwner {

    override fun ensureOwner(): AdminUser? {
        val email = normalizeEmail(ownerEmail)
        if (email.isEmpty() || ownerPasswordHash.isBlank()) {
            return null
        }

        val existing = adminUsers.findByEmail(email)
        if (existing != null) {
            check(existing.passwordHash == ownerPasswordHash) {
                "Refusing to claim $email as the platform owner: it exists with a different credential"
            }
            if (existing.role == AdminRole.OWNER) {
                return existing
            }
            return adminUsers.save(existing.copy(role = AdminRole.OWNER))
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
