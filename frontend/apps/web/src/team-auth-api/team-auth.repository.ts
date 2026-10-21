import { teamOverviewDtoSchema, teamSessionDtoSchema } from '@/team-auth-api/dtos/team-auth.dto.ts';
import { teamOverviewDtoToDomain, teamSessionDtoToDomain } from '@/team-auth-api/mappers/team-auth.mapper.ts';
import type { TeamOverview } from '@/domain/team-overview.domain.ts';
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

/**
 * The session-guarded read of the team's own data. Scoping comes from the httpOnly
 * cookie alone; a 401 here is the "session expired/gone" signal the UI reacts to
 * with a bounce to `/team/login`.
 */
async function me(): Promise<TeamOverview> {
    const { data } = await apiClient.get<unknown>('/api/team/me');
    return teamOverviewDtoToDomain(teamOverviewDtoSchema.parse(data));
}

export const TeamAuthRepository = {
    exchange,
    me,
};
