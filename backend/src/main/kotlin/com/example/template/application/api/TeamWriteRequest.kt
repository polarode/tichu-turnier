package com.example.template.application.api

/**
 * Transport-agnostic write payload for creating a team. The REST layer maps its
 * validated request DTO onto this; the use case never sees HTTP types.
 */
data class TeamWriteRequest(
    val name: String,
    val playerOneName: String,
    val playerTwoName: String,
)
