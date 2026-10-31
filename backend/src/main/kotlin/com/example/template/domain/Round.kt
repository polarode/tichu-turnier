package com.example.template.domain

/**
 * One iteration of the tournament: every team plays exactly one match, seated
 * according to the round's [pairing]. Created by the start-tournament use case
 * (round 1) and later by the round-advance flow (rounds 2+).
 */
data class Round(
    val id: RoundId,
    val tournamentId: TournamentId,
    /** 1-based round number within the tournament; round 1 is the first round. */
    val number: Int,
    val pairing: Pairing,
    /**
     * Minimal lock state (S5, #100): a locked round's games are read-only. The
     * confirm/retract workflow that flips it is S6 (#34).
     */
    val locked: Boolean = false,
)

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class RoundId(val value: String)
