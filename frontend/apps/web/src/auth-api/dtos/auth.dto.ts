import { z } from 'zod';

import { ADMIN_ROLES } from '@/vocabulary/admin.enum.ts';

/**
 * zod schemas describing the auth wire format. Responses are parsed at the repository
 * boundary so a contract drift fails loudly instead of leaking `any` through the app.
 */
export const adminDtoSchema = z.object({
    id: z.string(),
    email: z.string(),
    role: z.enum(ADMIN_ROLES),
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

/**
 * Registration extends the credentials shape with the optional raw invite token from
 * an invite link. The token is only ever sent to the backend; nothing persists it.
 */
export interface RegistrationRequestDto extends CredentialsRequestDto {
    inviteToken?: string;
}
