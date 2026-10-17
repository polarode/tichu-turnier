package com.example.template.domain

/**
 * Outbound port for id creation. Keeping it behind an interface lets tests supply
 * deterministic ids instead of random ones.
 */
interface IdGenerator {
    fun newId(): TaskId
}
