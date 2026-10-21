import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';

import { useExchangeAccessLink } from '@/features/teams/hooks/team-auth-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

type Phase = 'exchanging' | 'failed';

/** Shared scaffold for the three login states; the back link leads to the public start page. */
function Screen({
    titleKey,
    descriptionKey,
    children,
}: {
    titleKey: TranslationKey;
    descriptionKey: TranslationKey;
    children?: ReactNode;
}) {
    return (
        <section className="flex max-w-md flex-col gap-4">
            <PageHeader titleKey={titleKey} descriptionKey={descriptionKey} />
            {children}
        </section>
    );
}

function BackLink() {
    const { t } = useI18n();
    return (
        <Link to="/public" className="text-sm text-primary underline-offset-4 hover:underline">
            {t('teamLogin.backLink')}
        </Link>
    );
}

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
    const navigate = useNavigate();
    const exchange = useExchangeAccessLink();
    const tokenRef = useRef<string | undefined>(token);
    const [phase, setPhase] = useState<Phase>('exchanging');
    const exchangedRef = useRef(false);

    useEffect(() => {
        const rawToken = tokenRef.current;
        if (!rawToken || exchangedRef.current) return;
        exchangedRef.current = true;

        // Strip first — even a failing exchange must leave no token in the URL.
        void navigate({ to: '/team/login', search: {}, replace: true });

        exchange.mutate(rawToken, {
            onSuccess: () => {
                tokenRef.current = undefined;
                void navigate({ to: '/team', replace: true });
            },
            onError: () => {
                tokenRef.current = undefined;
                setPhase('failed');
            },
        });
        // Runs once per mount: the redemption must happen exactly once, so the
        // mutation instance is deliberately excluded from the dependencies.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [navigate]);

    if (phase === 'failed') {
        return (
            <Screen titleKey="teamLogin.errorTitle" descriptionKey="teamLogin.errorDescription">
                <BackLink />
            </Screen>
        );
    }

    if (!tokenRef.current) {
        return (
            <Screen
                titleKey="teamLogin.neutralTitle"
                descriptionKey="teamLogin.neutralDescription"
            >
                <BackLink />
            </Screen>
        );
    }

    return (
        <Screen
            titleKey="teamLogin.exchangingTitle"
            descriptionKey="teamLogin.exchangingDescription"
        />
    );
}
