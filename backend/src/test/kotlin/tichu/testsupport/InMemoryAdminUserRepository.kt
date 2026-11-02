package tichu.testsupport

import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository

/** In-memory double for [AdminUserRepository], used by pure use-case tests. */
class InMemoryAdminUserRepository : AdminUserRepository {

    private val users = LinkedHashMap<String, AdminUser>()

    override fun save(user: AdminUser): AdminUser {
        users[user.id.value] = user
        return user
    }

    override fun findByEmail(email: String): AdminUser? =
        users.values.firstOrNull { it.email == email }

    override fun findById(id: AdminUserId): AdminUser? = users[id.value]

    override fun findAll(): List<AdminUser> = users.values.toList()

    override fun findAllByStatus(status: AdminStatus): List<AdminUser> =
        users.values.filter { it.status == status }

    override fun deleteById(id: AdminUserId) {
        users.remove(id.value)
    }
}
