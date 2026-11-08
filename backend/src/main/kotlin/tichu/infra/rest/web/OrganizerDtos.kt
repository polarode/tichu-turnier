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
 * Wire representation of the organizer list: the lead organizer (email and disabled flag, since
 * the lead was never added) and the co-organizers in the order they were added.
 */
data class OrganizersResponse(
    val lead: LeadOrganizerResponse,
    val coOrganizers: List<CoOrganizerResponse>,
) {
    companion object {
        fun from(organizers: Organizers): OrganizersResponse = OrganizersResponse(
            lead = LeadOrganizerResponse(email = organizers.lead.email, disabled = organizers.lead.disabled),
            coOrganizers = organizers.coOrganizers.map {
                CoOrganizerResponse(
                    adminUserId = it.adminUserId.value,
                    email = it.email,
                    addedAt = it.addedAt,
                    disabled = it.disabled,
                )
            },
        )
    }
}

data class LeadOrganizerResponse(
    val email: String,
    val disabled: Boolean,
)

data class CoOrganizerResponse(
    val adminUserId: String,
    val email: String,
    val addedAt: Instant,
    val disabled: Boolean,
)
