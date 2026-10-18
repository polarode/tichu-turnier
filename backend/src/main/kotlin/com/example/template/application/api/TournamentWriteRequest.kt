package com.example.template.application.api

import com.example.template.domain.TournamentSettings

/**
 * The editable tournament payload shared by create and update. Both verbs accept the
 * same full representation (PUT replaces every editable field), so the shape lives in
 * one place instead of drifting between two near-identical request types.
 */
data class TournamentWriteRequest(
    val name: String,
    val description: String,
    val maxTeams: Int,
    val settings: TournamentSettings = TournamentSettings(),
)
