import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

type Phase = 'exchanging' | 'failed';

/**
 * The access-link redemption flow (see ADR 0001). On mount the raw token is stripped
 * from the address bar *before* the exchange runs, so the secret never persists in
 * the URL or browser history; the token is carried only in memory. On success the
 * session cookie is set and the team lands on `/team` (replace). Any rejection —
 * unknown, revoked, expired or rate-limited — shows the same localized dead-end
 * screen; the UI cannot and must not distinguish causes. `/team/login` without a
 * token shows the neutral "check your access link" prompt; there is no paste box.
 */
export function TeamLogin({ token }: { token?: string }) {
    const { t } = useI18n();
    const navigate = useNavigate();
    const tokenRef = useRef<string | undefined>(token);
    const [phase, setPhase] = useState<Phase>('exchanging');
    const exchangedRef = useRef(false);

    useEffect(() => {
        const rawToken = tokenRef.current;
        if (!rawToken || exchangedRef.current) return;
        exchangedRef.current = true;

        // Strip first — even a failing exchange must leave no token in the URL.
        void navigate({ to: '/team/login', search: {}, replace: true });

        TeamAuthRepository.exchange(rawToken)
            .then(() => {
                tokenRef.current = undefined;
                void navigate({ to: '/team', replace: true });
            })
            .catch(() => {
                tokenRef.current = undefined;
                setPhase('failed');
            });
    }, [navigate]);

    if (phase === 'failed') {
        return (
            <section className="flex max-w-md flex-col gap-4">
                <PageHeader titleKey="teamLogin.errorTitle" descriptionKey="teamLogin.errorDescription" />
                <Link to="/public" className="text-sm text-primary underline-offset-4 hover:underline">
                    {t('teamLogin.backLink')}
                </Link>
            </section>
        );
    }

    if (!tokenRef.current) {
        return (
            <section className="flex max-w-md flex-col gap-4">
                <PageHeader
                    titleKey="teamLogin.neutralTitle"
                    descriptionKey="teamLogin.neutralDescription"
                />
                <Link to="/public" className="text-sm text-primary underline-offset-4 hover:underline">
                    {t('teamLogin.backLink')}
                </Link>
            </section>
        );
    }

    return (
        <section className="flex max-w-md flex-col gap-4">
            <PageHeader
                titleKey="teamLogin.exchangingTitle"
                descriptionKey="teamLogin.exchangingDescription"
            />
        </section>
    );
}
