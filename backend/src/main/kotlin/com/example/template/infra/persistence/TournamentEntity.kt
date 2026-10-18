package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Persistence model for [Tournament]. Lives in the infra layer — the domain entity
 * stays framework-free; this is the wire/storage format of the JPA adapter.
 *
 * [TournamentSettings] is stored as a single JSONB column: settings belong together
 * as one organizer-facing unit and can grow (more toggles) without schema churn.
 * Hibernate maps it to the domain value object via Jackson.
 *
 * `owner_id` is `NOT NULL` in the database (see the tournament migration), so the
 * domain's non-null owner is a real invariant rather than a convention.
 */
@Entity
@Table(name = "tournaments")
class TournamentEntity(
    @Id
    val id: String,
    val ownerId: String,
    name: String,
    description: String,
    maxTeams: Int,
    settings: TournamentSettings,
    status: TournamentStatus,
    createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var maxTeams: Int = maxTeams
        private set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var settings: TournamentSettings = settings
        private set

    @Enumerated(EnumType.STRING)
    var status: TournamentStatus = status
        private set

    var createdAt: Instant = createdAt
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun toDomain(): Tournament = Tournament(
        id = TournamentId(id),
        ownerId = AdminUserId(ownerId),
        name = name,
        description = description,
        maxTeams = maxTeams,
        settings = settings,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(tournament: Tournament): TournamentEntity = TournamentEntity(
            id = tournament.id.value,
            ownerId = tournament.ownerId.value,
            name = tournament.name,
            description = tournament.description,
            maxTeams = tournament.maxTeams,
            settings = tournament.settings,
            status = tournament.status,
            createdAt = tournament.createdAt,
            updatedAt = tournament.updatedAt,
        )
    }
}
