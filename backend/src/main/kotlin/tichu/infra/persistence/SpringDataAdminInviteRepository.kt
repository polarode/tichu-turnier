package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [AdminInviteEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [tichu.domain.AdminInviteRepository].
 */
interface SpringDataAdminInviteRepository : JpaRepository<AdminInviteEntity, String> {
    fun findByTokenHash(tokenHash: String): AdminInviteEntity?
}
