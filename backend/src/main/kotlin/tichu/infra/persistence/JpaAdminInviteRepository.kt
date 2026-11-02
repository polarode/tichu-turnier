package tichu.infra.persistence

import tichu.domain.AdminInvite
import tichu.domain.AdminInviteRepository
import tichu.domain.InviteId

/**
 * Driven adapter implementing [AdminInviteRepository] on top of Spring Data JPA and
 * PostgreSQL (schema managed by Flyway).
 */
class JpaAdminInviteRepository(
    private val springData: SpringDataAdminInviteRepository,
) : AdminInviteRepository {

    override fun save(invite: AdminInvite): AdminInvite =
        springData.save(AdminInviteEntity.from(invite)).toDomain()

    override fun findByTokenHash(tokenHash: String): AdminInvite? =
        springData.findByTokenHash(tokenHash)?.toDomain()

    override fun findById(id: InviteId): AdminInvite? =
        springData.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findAll(): List<AdminInvite> =
        springData.findAll().map { it.toDomain() }
}
