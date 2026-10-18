package com.example.template.infra.rest

import com.example.template.application.api.ApproveAdmin
import com.example.template.application.api.CreateInvite
import com.example.template.application.api.CreateTeam
import com.example.template.application.api.CreateTournament
import com.example.template.application.api.DeleteTeam
import com.example.template.application.api.DeleteTournament
import com.example.template.application.api.DisableAdmin
import com.example.template.application.api.ExchangeTeamAccess
import com.example.template.application.api.GetOwnTeam
import com.example.template.application.api.GetTeam
import com.example.template.application.api.GetTournament
import com.example.template.application.api.ListAdmins
import com.example.template.application.api.ListInvites
import com.example.template.application.api.ListTeams
import com.example.template.application.api.ListTournaments
import com.example.template.application.api.ReactivateAdmin
import com.example.template.application.api.RejectAdmin
import com.example.template.application.api.RevokeInvite
import com.example.template.application.api.UpdateTeam
import com.example.template.application.api.UpdateTournament
import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.LogoutAdmin
import com.example.template.application.api.RefreshSession
import com.example.template.application.api.RegisterAdmin
import com.example.template.infra.rest.web.AdminController
import com.example.template.infra.rest.web.AuthController
import com.example.template.infra.rest.web.InviteController
import com.example.template.infra.rest.web.SessionCookieFactory
import com.example.template.infra.rest.web.TeamAuthController
import com.example.template.infra.rest.web.TeamController
import com.example.template.infra.rest.web.TournamentController
import com.example.template.infra.rest.web.WebControllerAdvice
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
    ): TournamentController =
        TournamentController(createTournament, listTournaments, getTournament, updateTournament, deleteTournament)

    @Bean
    fun teamController(
        createTeam: CreateTeam,
        listTeams: ListTeams,
        getTeam: GetTeam,
        updateTeam: UpdateTeam,
        deleteTeam: DeleteTeam,
    ): TeamController = TeamController(createTeam, listTeams, getTeam, updateTeam, deleteTeam)

    @Bean
    fun adminSessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(sessionCookieName, sessionCookieSecure, sessionTtl)

    @Bean
    fun teamSessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(teamSessionCookieName, sessionCookieSecure, teamSessionTtl)

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
