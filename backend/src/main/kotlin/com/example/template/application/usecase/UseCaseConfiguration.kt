package com.example.template.application.usecase

import com.example.template.application.api.AuthenticateSession
import com.example.template.application.api.CreateTournament
import com.example.template.application.api.GetTournament
import com.example.template.application.api.ListTournaments
import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.LogoutAdmin
import com.example.template.application.api.RefreshSession
import com.example.template.application.api.RegisterAdmin
import com.example.template.domain.AdminSessionRepository
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.PasswordHasher
import com.example.template.domain.SessionTokens
import com.example.template.domain.TournamentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/**
 * The only place use cases are turned into beans. They remain plain classes;
 * their dependencies (ports) are injected here as constructor arguments, which
 * Spring resolves from other beans. No @Service / @Autowired anywhere.
 */
@Configuration
class UseCaseConfiguration(
    @param:Value("\${app.auth.session-ttl}") private val sessionTtl: Duration,
) {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun createTournament(
        repository: TournamentRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateTournament = CreateTournamentCommand(repository, idGenerator, clock)

    @Bean
    fun listTournaments(repository: TournamentRepository): ListTournaments =
        ListTournamentsQuery(repository)

    @Bean
    fun getTournament(repository: TournamentRepository): GetTournament =
        GetTournamentQuery(repository)

    @Bean
    fun adminSessionIssuer(
        sessions: AdminSessionRepository,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
    ): AdminSessionIssuer = AdminSessionIssuer(sessions, sessionTokens, idGenerator, sessionTtl)

    @Bean
    fun registerAdmin(
        adminUsers: AdminUserRepository,
        passwordHasher: PasswordHasher,
        idGenerator: IdGenerator,
        clock: Clock,
    ): RegisterAdmin = RegisterAdminCommand(adminUsers, passwordHasher, idGenerator, clock)

    @Bean
    fun authenticateSession(
        sessions: AdminSessionRepository,
        adminUsers: AdminUserRepository,
        sessionTokens: SessionTokens,
        clock: Clock,
    ): AuthenticateSession =
        AuthenticateSessionQuery(sessions, adminUsers, sessionTokens, clock)

    @Bean
    fun loginAdmin(
        adminUsers: AdminUserRepository,
        passwordHasher: PasswordHasher,
        sessionIssuer: AdminSessionIssuer,
        clock: Clock,
    ): LoginAdmin = LoginAdminCommand(adminUsers, passwordHasher, sessionIssuer, clock)

    @Bean
    fun refreshSession(
        sessions: AdminSessionRepository,
        adminUsers: AdminUserRepository,
        sessionTokens: SessionTokens,
        sessionIssuer: AdminSessionIssuer,
        clock: Clock,
    ): RefreshSession =
        RefreshSessionCommand(sessions, adminUsers, sessionTokens, sessionIssuer, clock)

    @Bean
    fun logoutAdmin(
        sessions: AdminSessionRepository,
        sessionTokens: SessionTokens,
    ): LogoutAdmin = LogoutAdminCommand(sessions, sessionTokens)
}
