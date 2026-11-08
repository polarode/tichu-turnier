import { z } from 'zod';

/**
 * zod schemas for the organizer list wire format (`GET` and `POST
 * /api/tournaments/{id}/organizers`). The lead carries only an email; each co-organizer
 * carries the ISO instant they were added.
 */
export const organizersDtoSchema = z.object({
    lead: z.object({ email: z.string() }),
    coOrganizers: z.array(
        z.object({
            email: z.string(),
            addedAt: z.string(),
        }),
    ),
});

export type OrganizersDto = z.infer<typeof organizersDtoSchema>;

/** The body of `POST /api/tournaments/{id}/organizers`. */
export interface AddCoOrganizerRequestDto {
    email: string;
}
