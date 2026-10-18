package com.example.template.domain

/**
 * Outbound port for id creation. Keeping it behind an interface lets tests supply
 * deterministic ids instead of random ones. Returns a bare string so every entity
 * type can wrap it in its own typed id.
 */
interface IdGenerator {
    fun newId(): String
}
