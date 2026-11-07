package tichu.infra.rest

import tichu.application.api.ApproveAdmin
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
import tichu.application.api.RevealTeamAccessLink
import tichu.application.api.RotateTeamAccessLink
import tichu.application.api.RevokeTeamAccessLink
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
import tichu.infra.events.SseTournamentEventBroadcaster
import tichu.infra.rest.web.AdminController
import tichu.infra.rest.web.AuthController
import tichu.infra.rest.web.FinalRankingController
import tichu.infra.rest.web.GameController
import tichu.infra.rest.web.InviteController
import tichu.infra.rest.web.SessionCookieFactory
import tichu.infra.rest.web.TeamAuthController
import tichu.infra.rest.web.TeamController
import tichu.infra.rest.web.TableConfirmationController
import tichu.infra.rest.web.TeamScoringController
import tichu.infra.rest.web.TournamentController
import tichu.infra.rest.web.TournamentEventsController
import tichu.infra.rest.web.WebControllerAdvice
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Wires the web adapters as beans. Controllers receive inbound ports as
 * constructor args, resolved from the beans declared in UseCaseConfiguration.
 */
@Configuration
class RestConfiguration(
    @param:Value("\${app.auth.session-cookie-name}") private val sessionCookieName: String,
    @param:Value("\${app.auth.session-cookie-secure}") private val sessionCookieSecure: Boolean,
    @param:Value("\${app.auth.session-ttl}") private val sessionTtl: Duration,
    @param:Value("\${app.auth.team-session-cookie-name}") private val teamSessionCookieName: String,
    @param:Value("\${app.auth.team-session-ttl}") private val teamSessionTtl: Duration,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {

    @Bean
    fun tournamentController(
        createTournament: CreateTournament,
        listTournaments: ListTournaments,
        getTournament: GetTournament,
        updateTournament: UpdateTournament,
        deleteTournament: DeleteTournament,
        startTournament: StartTournament,
        listRounds: ListRounds,
        getStandings: GetStandings,
        setTotalRounds: SetTotalRounds,
        startNextRound: StartNextRound,
        finishTournament: FinishTournament,
        publishTournament: PublishTournament,
    ): TournamentController = TournamentController(
        createTournament,
        listTournaments,
        getTournament,
        updateTournament,
        deleteTournament,
        startTournament,
        listRounds,
        getStandings,
        setTotalRounds,
        startNextRound,
        finishTournament,
        publishTournament,
    )

    @Bean
    fun tournamentEventsController(
        getTournament: GetTournament,
        tournamentEventBroadcaster: SseTournamentEventBroadcaster,
    ): TournamentEventsController = TournamentEventsController(getTournament, tournamentEventBroadcaster)

    @Bean
    fun teamController(
        createTeam: CreateTeam,
        listTeams: ListTeams,
        getTeam: GetTeam,
        updateTeam: UpdateTeam,
        deleteTeam: DeleteTeam,
        revealTeamAccessLink: RevealTeamAccessLink,
        rotateTeamAccessLink: RotateTeamAccessLink,
        revokeTeamAccessLink: RevokeTeamAccessLink,
    ): TeamController = TeamController(
        createTeam,
        listTeams,
        getTeam,
        updateTeam,
        deleteTeam,
        revealTeamAccessLink,
        rotateTeamAccessLink,
        revokeTeamAccessLink,
        frontendUrl,
    )

    @Bean
    fun adminSessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(sessionCookieName, sessionCookieSecure, sessionTtl)

    @Bean
    fun teamSessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(teamSessionCookieName, sessionCookieSecure, teamSessionTtl)

    @Bean
    fun gameController(recordGame: RecordGame): GameController = GameController(recordGame)

    @Bean
    fun teamScoringController(getTeamScoring: GetTeamScoring): TeamScoringController =
        TeamScoringController(getTeamScoring)

    @Bean
    fun finalRankingController(getFinalRanking: GetFinalRanking): FinalRankingController =
        FinalRankingController(getFinalRanking)

    @Bean
    fun tableConfirmationController(confirmTable: ConfirmTable): TableConfirmationController =
        TableConfirmationController(confirmTable)

    @Bean
    fun authController(
        registerAdmin: RegisterAdmin,
        loginAdmin: LoginAdmin,
        refreshSession: RefreshSession,
        logoutAdmin: LogoutAdmin,
        adminSessionCookieFactory: SessionCookieFactory,
    ): AuthController =
        AuthController(registerAdmin, loginAdmin, refreshSession, logoutAdmin, adminSessionCookieFactory)

    @Bean
    fun teamAuthController(
        exchangeTeamAccess: ExchangeTeamAccess,
        getOwnTeam: GetOwnTeam,
        teamSessionCookieFactory: SessionCookieFactory,
    ): TeamAuthController =
        TeamAuthController(exchangeTeamAccess, getOwnTeam, teamSessionCookieFactory)

    @Bean
    fun adminController(
        listAdmins: ListAdmins,
        approveAdmin: ApproveAdmin,
        rejectAdmin: RejectAdmin,
        disableAdmin: DisableAdmin,
        reactivateAdmin: ReactivateAdmin,
    ): AdminController =
        AdminController(listAdmins, approveAdmin, rejectAdmin, disableAdmin, reactivateAdmin)

    @Bean
    fun inviteController(
        createInvite: CreateInvite,
        listInvites: ListInvites,
        revokeInvite: RevokeInvite,
    ): InviteController = InviteController(createInvite, listInvites, revokeInvite, frontendUrl)

    @Bean
    fun webControllerAdvice(): WebControllerAdvice = WebControllerAdvice()
}
