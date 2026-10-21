import { useMutation } from '@tanstack/react-query';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import type { TeamSession } from '@/domain/team-session.domain.ts';

/**
 * Exchanges a raw access-link token for the httpOnly team session. Every rejection —
 * unknown, revoked, expired or rate-limited — arrives as the same failure (ADR 0001),
 * so callers treat errors uniformly and never retry.
 */
export function useExchangeAccessLink() {
    return useMutation({
        mutationFn: (token: string): Promise<TeamSession> => TeamAuthRepository.exchange(token),
    });
}
