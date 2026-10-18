package com.example.template.domain

import java.time.Instant

/**
 * The core domain entity of the tournament setup phase. Framework-free: no Spring,
 * no persistence annotations.
 *
 * [ownerId] is the admin who created it: every read is scoped to that owner, so a
 * tournament can never be loaded or changed by another admin's session.
 */
data class Tournament(
    val id: TournamentId,
    val ownerId: AdminUserId,
    val name: String,
    val description: String,
    val maxTeams: Int,
    val settings: TournamentSettings,
    val status: TournamentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isOwnedBy(owner: AdminUserId): Boolean = ownerId == owner
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class TournamentId(val value: String)

/**
 * Status machine: `setup → active → completed`, final — no reopen. The transitions
 * themselves are enforced by later slices (start tournament / finish tournament);
 * a newly created tournament always starts in [SETUP].
 */
enum class TournamentStatus {
    SETUP,
    ACTIVE,
    COMPLETED,
}

/**
 * Organizer-configurable tournament rules, stored as one unit and validated
 * server-side. New settings belong here, not as extra entity columns scattered
 * across the domain.
 */
data class TournamentSettings(
    /** Whether grand Tichu announcements are accepted in game scoring (Phase 8 enforces it). */
    val allowGrandTichu: Boolean = true,
)
