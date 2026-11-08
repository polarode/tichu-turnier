package tichu.infra.rest.web

import tichu.application.api.Organizers
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/** The body of `POST /api/tournaments/{id}/organizers`: the email of an active admin (#131). */
data class AddCoOrganizerRequestBody(
    @field:NotBlank(message = "must not be blank")
    val email: String,
)

/**
 * Wire representation of the organizer list: the lead organizer (email only, since the lead
 * was never added) and the co-organizers in the order they were added.
 */
data class OrganizersResponse(
    val lead: LeadOrganizerResponse,
    val coOrganizers: List<CoOrganizerResponse>,
) {
    companion object {
        fun from(organizers: Organizers): OrganizersResponse = OrganizersResponse(
            lead = LeadOrganizerResponse(email = organizers.lead.email),
            coOrganizers = organizers.coOrganizers.map { organizer ->
                CoOrganizerResponse(
                    email = organizer.email,
                    addedAt = requireNotNull(organizer.addedAt) { "a co-organizer always has an addedAt" },
                )
            },
        )
    }
}

data class LeadOrganizerResponse(
    val email: String,
)

data class CoOrganizerResponse(
    val email: String,
    val addedAt: Instant,
)
