package tichu.application.usecase

import tichu.application.api.ApproveAdmin
import tichu.application.api.AuthenticateSession
import tichu.application.api.AuthenticateTeamSession
import tichu.application.api.BootstrapOwner
import tichu.application.api.ConfirmTable
import tichu.application.api.CreateInvite
import tichu.application.api.CreateTeam
import tichu.application.api.CreateTournament
import tichu.application.api.DeleteTeam
import tichu.application.api.DeleteTournament
import tichu.application.api.DisableAdmin
import tichu.application.api.ExchangeTeamAccess
import tichu.application.api.FinishTournament
import tichu.application.api.GetFinalRanking
import tichu.application.api.GetOwnTeam
import tichu.application.api.GetTeam
import tichu.application.api.GetTeamScoring
import tichu.application.api.GetTournament
import tichu.application.api.ListAdmins
import tichu.application.api.ListInvites
import tichu.application.api.ListTeams
import tichu.application.api.ListTournaments
import tichu.application.api.PublishTournament
import tichu.application.api.RecordGame
import tichu.application.api.ReactivateAdmin
import tichu.application.api.RejectAdmin
import tichu.application.api.RevokeInvite
import tichu.application.api.UpdateTeam
import tichu.application.api.UpdateTournament
import tichu.application.api.LoginAdmin
import tichu.application.api.LogoutAdmin
import tichu.application.api.RefreshSession
import tichu.application.api.RegisterAdmin
import tichu.application.api.GetStandings
import tichu.application.api.ListRounds
import tichu.application.api.SetTotalRounds
import tichu.application.api.StartNextRound
import tichu.application.api.StartTournament
import tichu.domain.AdminInviteRepository
import tichu.domain.AdminSessionRepository
import tichu.domain.AdminUserRepository
import tichu.domain.GameRepository
import tichu.domain.IdGenerator
import tichu.domain.PasswordHasher
import tichu.domain.RoundRepository
import tichu.domain.TableConfirmationRepository
import tichu.domain.SessionTokens
import tichu.domain.TeamRepository
import tichu.domain.TeamSessionRepository
import tichu.domain.TeamTokenRepository
import tichu.domain.TournamentEvents
import tichu.domain.TournamentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import kotlin.random.Random

/**
 * The only place use cases are turned into beans. They remain plain classes;
 * their dependencies (ports) are injected here as constructor arguments, which
 * Spring resolves from other beans. No @Service / @Autowired anywhere.
 */
@Configuration
class UseCaseConfiguration(
    @param:Value("\${app.auth.session-ttl}") private val sessionTtl: Duration,
    @param:Value("\${app.auth.team-session-ttl}") private val teamSessionTtl: Duration,
    @param:Value("\${app.auth.invite-ttl}") private val inviteTtl: Duration,
    @param:Value("\${app.auth.exchange-rate-limit.max-failures:5}") private val exchangeMaxFailures: Int,
    @param:Value("\${app.auth.exchange-rate-limit.window:PT15M}") private val exchangeWindow: Duration,
    @param:Value("\${app.auth.owner.email:}") private val ownerEmail: String,
    @param:Value("\${app.auth.owner.password-hash:}") private val ownerPasswordHash: String,
) {

    @Bean
    fun startTournament(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        roundRepository: RoundRepository,
        idGenerator: IdGenerator,
        random: Random,
        clock: Clock,
        tournamentEvents: TournamentEvents,
    ): StartTournament =
        StartTournamentCommand(
            tournamentRepository, teamRepository, roundRepository, idGenerator, random, clock, tournamentEvents,
        )

    @Bean
    fun playedRoundsReader(
        roundRepository: RoundRepository,
        gameRepository: GameRepository,
        confirmationRepository: TableConfirmationRepository,
    ): PlayedRoundsReader = PlayedRoundsReader(roundRepository, gameRepository, confirmationRepository)

    @Bean
    fun startNextRound(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        roundRepository: RoundRepository,
        playedRoundsReader: PlayedRoundsReader,
        idGenerator: IdGenerator,
        random: Random,
        tournamentEvents: TournamentEvents,
    ): StartNextRound =
        StartNextRoundCommand(
            tournamentRepository, teamRepository, roundRepository, playedRoundsReader, idGenerator, random, tournamentEvents,
        )

    @Bean
    fun finishTournament(
        tournamentRepository: TournamentRepository,
        playedRoundsReader: PlayedRoundsReader,
        clock: Clock,
        tournamentEvents: TournamentEvents,
    ): FinishTournament = FinishTournamentCommand(tournamentRepository, playedRoundsReader, clock, tournamentEvents)

    @Bean
    fun publishTournament(
        tournamentRepository: TournamentRepository,
        clock: Clock,
        tournamentEvents: TournamentEvents,
    ): PublishTournament = PublishTournamentCommand(tournamentRepository, clock, tournamentEvents)

    @Bean
    fun listRounds(
        tournamentRepository: TournamentRepository,
        roundRepository: RoundRepository,
        gameRepository: GameRepository,
        confirmationRepository: TableConfirmationRepository,
    ): ListRounds = ListRoundsQuery(tournamentRepository, roundRepository, gameRepository, confirmationRepository)

    @Bean
    fun getStandings(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        playedRoundsReader: PlayedRoundsReader,
    ): GetStandings = GetStandingsQuery(tournamentRepository, teamRepository, playedRoundsReader)

    @Bean
    fun getFinalRanking(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        playedRoundsReader: PlayedRoundsReader,
    ): GetFinalRanking = GetFinalRankingQuery(tournamentRepository, teamRepository, playedRoundsReader)

    @Bean
    fun setTotalRounds(
        tournamentRepository: TournamentRepository,
        roundRepository: RoundRepository,
        clock: Clock,
        tournamentEvents: TournamentEvents,
    ): SetTotalRounds = SetTotalRoundsCommand(tournamentRepository, roundRepository, clock, tournamentEvents)

    @Bean
    fun recordGame(
        tournamentRepository: TournamentRepository,
        roundRepository: RoundRepository,
        teamRepository: TeamRepository,
        gameRepository: GameRepository,
        confirmationRepository: TableConfirmationRepository,
        tournamentEvents: TournamentEvents,
    ): RecordGame =
        RecordGameCommand(
            tournamentRepository, roundRepository, teamRepository, gameRepository, confirmationRepository,
            tournamentEvents,
        )

    @Bean
    fun confirmTable(
        tournamentRepository: TournamentRepository,
        roundRepository: RoundRepository,
        gameRepository: GameRepository,
        confirmationRepository: TableConfirmationRepository,
        clock: Clock,
        tournamentEvents: TournamentEvents,
    ): ConfirmTable =
        ConfirmTableCommand(
            roundRepository, tournamentRepository, gameRepository, confirmationRepository, clock, tournamentEvents,
        )

    @Bean
    fun random(): Random = Random.Default

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun bootstrapOwner(
        adminUsers: AdminUserRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): BootstrapOwner = BootstrapOwnerCommand(adminUsers, idGenerator, clock, ownerEmail, ownerPasswordHash)

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
    fun updateTournament(
        repository: TournamentRepository,
        teamRepository: TeamRepository,
        clock: Clock,
    ): UpdateTournament = UpdateTournamentCommand(repository, teamRepository, clock)

    @Bean
    fun deleteTournament(repository: TournamentRepository): DeleteTournament =
        DeleteTournamentCommand(repository)

    @Bean
    fun createTeam(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        teamTokenRepository: TeamTokenRepository,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateTeam =
        CreateTeamCommand(tournamentRepository, teamRepository, teamTokenRepository, sessionTokens, idGenerator, clock)

    @Bean
    fun listTeams(tournamentRepository: TournamentRepository, teamRepository: TeamRepository): ListTeams =
        ListTeamsQuery(tournamentRepository, teamRepository)

    @Bean
    fun getTeam(tournamentRepository: TournamentRepository, teamRepository: TeamRepository): GetTeam =
        GetTeamQuery(tournamentRepository, teamRepository)

    @Bean
    fun updateTeam(tournamentRepository: TournamentRepository, teamRepository: TeamRepository): UpdateTeam =
        UpdateTeamCommand(tournamentRepository, teamRepository)

    @Bean
    fun deleteTeam(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        teamTokenRepository: TeamTokenRepository,
    ): DeleteTeam = DeleteTeamCommand(tournamentRepository, teamRepository, teamTokenRepository)

    @Bean
    fun teamSessionIssuer(
        teamSessions: TeamSessionRepository,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
    ): TeamSessionIssuer = TeamSessionIssuer(teamSessions, sessionTokens, idGenerator, teamSessionTtl)

    @Bean
    fun exchangeTeamAccess(
        teamTokens: TeamTokenRepository,
        teamRepository: TeamRepository,
        tournamentRepository: TournamentRepository,
        sessionTokens: SessionTokens,
        teamSessionIssuer: TeamSessionIssuer,
        clock: Clock,
    ): ExchangeTeamAccess =
        ExchangeTeamAccessCommand(teamTokens, teamRepository, tournamentRepository, sessionTokens, teamSessionIssuer, exchangeRateLimiter(), clock)

    @Bean
    fun authenticateTeamSession(
        teamSessions: TeamSessionRepository,
        teamRepository: TeamRepository,
        sessionTokens: SessionTokens,
        clock: Clock,
    ): AuthenticateTeamSession =
        AuthenticateTeamSessionQuery(teamSessions, teamRepository, sessionTokens, clock)

    @Bean
    fun getOwnTeam(teamRepository: TeamRepository, tournamentRepository: TournamentRepository): GetOwnTeam =
        GetOwnTeamQuery(teamRepository, tournamentRepository)

    @Bean
    fun getTeamScoring(
        tournamentRepository: TournamentRepository,
        roundRepository: RoundRepository,
        teamRepository: TeamRepository,
        gameRepository: GameRepository,
        confirmationRepository: TableConfirmationRepository,
    ): GetTeamScoring =
        GetTeamScoringQuery(tournamentRepository, roundRepository, teamRepository, gameRepository, confirmationRepository)

    @Bean
    fun adminSessionIssuer(
        sessions: AdminSessionRepository,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
    ): AdminSessionIssuer = AdminSessionIssuer(sessions, sessionTokens, idGenerator, sessionTtl)

    @Bean
    fun registerAdmin(
        adminUsers: AdminUserRepository,
        adminInvites: AdminInviteRepository,
        passwordHasher: PasswordHasher,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
        clock: Clock,
    ): RegisterAdmin =
        RegisterAdminCommand(adminUsers, adminInvites, passwordHasher, sessionTokens, idGenerator, clock)

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

    @Bean
    fun listAdmins(adminUsers: AdminUserRepository): ListAdmins = ListAdminsQuery(adminUsers)

    @Bean
    fun approveAdmin(adminUsers: AdminUserRepository, clock: Clock): ApproveAdmin =
        ApproveAdminCommand(adminUsers, clock)

    @Bean
    fun rejectAdmin(adminUsers: AdminUserRepository): RejectAdmin = RejectAdminCommand(adminUsers)

    @Bean
    fun disableAdmin(adminUsers: AdminUserRepository): DisableAdmin = DisableAdminCommand(adminUsers)

    @Bean
    fun reactivateAdmin(adminUsers: AdminUserRepository): ReactivateAdmin = ReactivateAdminCommand(adminUsers)

    @Bean
    fun createInvite(
        adminInvites: AdminInviteRepository,
        sessionTokens: SessionTokens,
        idGenerator: IdGenerator,
        clock: Clock,
    ): CreateInvite =
        CreateInviteCommand(adminInvites, sessionTokens, idGenerator, clock, inviteTtl)

    @Bean
    fun listInvites(adminInvites: AdminInviteRepository): ListInvites = ListInvitesQuery(adminInvites)

    @Bean
    fun revokeInvite(adminInvites: AdminInviteRepository): RevokeInvite = RevokeInviteCommand(adminInvites)

    @Bean
    fun exchangeRateLimiter(): ExchangeRateLimiter = ExchangeRateLimiter(clock(), exchangeMaxFailures, exchangeWindow)

    @Bean
    fun manageTeamAccessLink(
        tournamentRepository: TournamentRepository,
        teamRepository: TeamRepository,
        teamTokenRepository: TeamTokenRepository,
        teamSessionRepository: TeamSessionRepository,
        sessionTokens: SessionTokens,
        clock: Clock,
    ): ManageTeamAccessLinkCommand =
        ManageTeamAccessLinkCommand(
            tournamentRepository,
            teamRepository,
            teamTokenRepository,
            teamSessionRepository,
            sessionTokens,
            clock,
        )
}
