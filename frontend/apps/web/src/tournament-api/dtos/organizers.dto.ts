import { z } from 'zod';

/**
 * zod schemas for the organizer list wire format (`GET` and `POST
 * /api/tournaments/{id}/organizers`). The lead carries only an email and the disabled flag;
 * each co-organizer carries their admin id, the ISO instant they were added and the disabled flag.
 */
export const organizersDtoSchema = z.object({
    lead: z.object({
        email: z.string(),
        disabled: z.boolean(),
    }),
    coOrganizers: z.array(
        z.object({
            adminUserId: z.string(),
            email: z.string(),
            addedAt: z.string(),
            disabled: z.boolean(),
        }),
    ),
});

export type OrganizersDto = z.infer<typeof organizersDtoSchema>;

/** The body of `POST /api/tournaments/{id}/organizers`. */
export interface AddCoOrganizerRequestDto {
    email: string;
}
