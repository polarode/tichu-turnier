package com.example.template.infra.rest

import com.example.template.application.api.CreateTournament
import com.example.template.application.api.DeleteTournament
import com.example.template.application.api.GetTournament
import com.example.template.application.api.ListTournaments
import com.example.template.application.api.UpdateTournament
import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.LogoutAdmin
import com.example.template.application.api.RefreshSession
import com.example.template.application.api.RegisterAdmin
import com.example.template.infra.rest.web.AuthController
import com.example.template.infra.rest.web.SessionCookieFactory
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
    fun sessionCookieFactory(): SessionCookieFactory =
        SessionCookieFactory(sessionCookieName, sessionCookieSecure, sessionTtl)

    @Bean
    fun authController(
        registerAdmin: RegisterAdmin,
        loginAdmin: LoginAdmin,
        refreshSession: RefreshSession,
        logoutAdmin: LogoutAdmin,
        sessionCookie: SessionCookieFactory,
    ): AuthController =
        AuthController(registerAdmin, loginAdmin, refreshSession, logoutAdmin, sessionCookie)

    @Bean
    fun webControllerAdvice(): WebControllerAdvice = WebControllerAdvice()
}
