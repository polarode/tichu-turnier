package com.example.template.application.usecase

import com.example.template.application.api.BootstrapOwner
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired as a
 * bean in [UseCaseConfiguration] — never annotated with @Service — and run once at
 * startup by an `ApplicationRunner`.
 *
 * The owner's credentials come from configuration as a pre-computed password hash, so
 * the bootstrap never handles a plaintext secret. Running it again is safe: an account
 * with the configured email is promoted in place rather than duplicated, and its
 * password is left untouched.
 */
class BootstrapOwnerCommand(
    private val adminUsers: AdminUserRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val ownerEmail: String,
    private val ownerPasswordHash: String,
) : BootstrapOwner {

    override fun ensureOwner(): AdminUser? {
        val email = ownerEmail.trim().lowercase()
        if (email.isEmpty() || ownerPasswordHash.isBlank()) {
            return null
        }

        val existing = adminUsers.findByEmail(email)
        if (existing != null) {
            if (existing.role == AdminRole.OWNER && existing.status == AdminStatus.ACTIVE) {
                return existing
            }
            return adminUsers.save(existing.copy(role = AdminRole.OWNER, status = AdminStatus.ACTIVE))
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
