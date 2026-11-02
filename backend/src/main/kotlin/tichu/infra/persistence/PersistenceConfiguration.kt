package tichu.infra.persistence

import tichu.domain.AdminInviteRepository
import tichu.domain.AdminSessionRepository
import tichu.domain.AdminUserRepository
import tichu.domain.GameRepository
import tichu.domain.IdGenerator
import tichu.domain.TeamRepository
import tichu.domain.TeamSessionRepository
import tichu.domain.TeamTokenRepository
import tichu.domain.TournamentRepository
import tichu.domain.RoundRepository
import tichu.domain.TableConfirmationRepository
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/**
 * Wiring for the persistence adapter. The repository ports are implemented by the
 * JPA adapters on PostgreSQL; the schema is created and migrated by Flyway.
 *
 * `@EnableJpaRepositories` / `@EntityScan` point Spring Data at this package —
 * `Application` does no classpath scanning, so discovery must be explicit here.
 */
@Configuration
@EnableJpaRepositories(basePackages = ["tichu.infra.persistence"])
@EntityScan(basePackages = ["tichu.infra.persistence"])
@EnableScheduling
class PersistenceConfiguration(
    private val springDataTournamentRepository: SpringDataTournamentRepository,
    private val springDataAdminUserRepository: SpringDataAdminUserRepository,
    private val springDataAdminInviteRepository: SpringDataAdminInviteRepository,
    private val springDataAdminSessionRepository: SpringDataAdminSessionRepository,
    private val springDataTeamRepository: SpringDataTeamRepository,
    private val springDataPlayerRepository: SpringDataPlayerRepository,
    private val springDataTeamTokenRepository: SpringDataTeamTokenRepository,
    private val springDataTeamSessionRepository: SpringDataTeamSessionRepository,
    private val springDataRoundRepository: SpringDataRoundRepository,
    private val springDataRoundTableRepository: SpringDataRoundTableRepository,
    private val springDataGameRepository: SpringDataGameRepository,
    private val springDataGameParticipantRepository: SpringDataGameParticipantRepository,
    private val springDataTableConfirmationRepository: SpringDataTableConfirmationRepository,
    private val entityManagerFactory: jakarta.persistence.EntityManagerFactory,
) {

    @Bean
    fun tournamentRepository(): TournamentRepository =
        JpaTournamentRepository(springDataTournamentRepository)

    @Bean
    fun teamRepository(): TeamRepository =
        JpaTeamRepository(springDataTeamRepository, springDataPlayerRepository)

    @Bean
    fun roundRepository(): RoundRepository =
        JpaRoundRepository(springDataRoundRepository, springDataRoundTableRepository)

    @Bean
    fun gameRepository(): GameRepository = JpaGameRepository(
        springDataGameRepository,
        springDataGameParticipantRepository,
        // A shared (thread-safe) proxy; the adapter only ever detaches entities.
        org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(
            entityManagerFactory,
        ),
    )

    @Bean
    fun tableConfirmationRepository(): TableConfirmationRepository =
        JpaTableConfirmationRepository(springDataTableConfirmationRepository)

    @Bean
    fun teamTokenRepository(): TeamTokenRepository =
        JpaTeamTokenRepository(springDataTeamTokenRepository)

    @Bean
    fun teamSessionRepository(): TeamSessionRepository =
        JpaTeamSessionRepository(springDataTeamSessionRepository)

    @Bean
    fun adminUserRepository(): AdminUserRepository =
        JpaAdminUserRepository(springDataAdminUserRepository)

    @Bean
    fun adminInviteRepository(): AdminInviteRepository =
        JpaAdminInviteRepository(springDataAdminInviteRepository)

    @Bean
    fun adminSessionRepository(): AdminSessionRepository =
        JpaAdminSessionRepository(springDataAdminSessionRepository)

    @Bean
    fun idGenerator(): IdGenerator = UuidIdGenerator()

    @Bean
    fun expiredSessionSweeper(
        adminSessionRepository: AdminSessionRepository,
        teamSessionRepository: TeamSessionRepository,
        clock: Clock,
    ): ExpiredSessionSweeper = ExpiredSessionSweeper(adminSessionRepository, teamSessionRepository, clock)
}
