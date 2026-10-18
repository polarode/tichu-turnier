import { z } from 'zod';

/**
 * zod schemas describing the auth wire format. Responses are parsed at the repository
 * boundary so a contract drift fails loudly instead of leaking `any` through the app.
 */
export const adminDtoSchema = z.object({
    id: z.string(),
    email: z.string(),
});

export type AdminDto = z.infer<typeof adminDtoSchema>;

/**
 * Register and login share one wire shape (email + password). The stricter password
 * rules live in the registration form schema, not here — the client should not decide
 * what the server accepts.
 */
export interface CredentialsRequestDto {
    email: string;
    password: string;
}
