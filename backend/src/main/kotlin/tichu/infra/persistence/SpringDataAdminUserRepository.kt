package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [AdminUserEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [tichu.domain.AdminUserRepository].
 */
interface SpringDataAdminUserRepository : JpaRepository<AdminUserEntity, String> {
    fun findByEmail(email: String): AdminUserEntity?

    fun findByStatus(status: String): List<AdminUserEntity>
}
