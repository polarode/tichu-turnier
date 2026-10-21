import { teamSessionDtoSchema } from '@/team-auth-api/dtos/team-auth.dto.ts';
import { teamSessionDtoToDomain } from '@/team-auth-api/mappers/team-auth.mapper.ts';
import type { TeamSession } from '@/domain/team-session.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the team-auth endpoints and wire DTOs. The exchange
 * consumes a raw access link token once and sets the httpOnly team-session cookie
 * via `Set-Cookie`; the raw token never appears in a response body.
 *
 * Every rejection — unknown, revoked, expired or rate-limited token — arrives as the
 * same 401 `TEAM_ACCESS_INVALID` problem detail (ADR 0001), so the caller cannot and
 * must not distinguish causes.
 */
async function exchange(token: string): Promise<TeamSession> {
    const { data } = await apiClient.post<unknown>('/api/team/auth/exchange', { token });
    return teamSessionDtoToDomain(teamSessionDtoSchema.parse(data));
}

export const TeamAuthRepository = {
    exchange,
};
