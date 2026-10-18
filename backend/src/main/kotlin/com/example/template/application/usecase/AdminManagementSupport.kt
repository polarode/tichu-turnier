package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.NotFoundException

/**
 * Role check shared by every owner-only use case. It lives in the application layer, so
 * the rule holds no matter which adapter drives the use case, and it fails with the
 * domain's forbidden error rather than a transport concern. The role comes from the
 * session principal, which is re-read from the database on every request.
 */
internal fun AuthenticatedAdmin.requireOwner() {
    if (role != AdminRole.OWNER) {
        throw ForbiddenException("Only the platform owner may manage admin accounts")
    }
}

/**
 * Loads the account an admin-management use case is targeting, mapping a missing id to
 * the ordinary not-found failure. A target the caller cannot see and one that does not
 * exist are the same answer, so no account ids are confirmed by probing.
 */
internal fun AdminUserRepository.requireById(id: AdminUserId): AdminUser =
    findById(id) ?: throw NotFoundException("Admin account not found")

/**
 * Refuses an email that already has an account. Registration and invite issuance share
 * the rule so neither can create a second account (or a dead invite) for a taken email.
 */
internal fun AdminUserRepository.requireEmailFree(email: String) {
    if (findByEmail(email) != null) {
        throw EmailAlreadyRegisteredException("An account with this email already exists")
    }
}
