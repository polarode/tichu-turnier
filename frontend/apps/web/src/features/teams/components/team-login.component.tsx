import { useEffect, useState, type ReactNode } from 'react';
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
 * The access-link redemption flow (see ADR 0001). The raw token is stripped from the
 * address bar *before* the exchange runs, so the secret never persists in the URL or
 * browser history; the token is carried only in memory. On success the session cookie
 * is set and the team lands on `/team` (replace). Any rejection — unknown, revoked,
 * expired or rate-limited — shows the same localized dead-end screen; the UI cannot
 * and must not distinguish causes. Without a token the screen shows the neutral
 * "check your access link" prompt; there is no paste box.
 */
export function TeamLogin({ token }: { token?: string }) {
    const navigate = useNavigate();
    const exchange = useExchangeAccessLink();
    // The token being redeemed lives in memory only — never in the URL, history or
    // any store. `redeemedRef` pins exactly-one-exchange per token, including the
    // case where the screen was already mounted (neutral prompt) when the token
    // arrives via a redirect.
    const [redeemedToken, setRedeemedToken] = useState<string | undefined>(undefined);
    const [phase, setPhase] = useState<Phase>('exchanging');

    useEffect(() => {
        if (!token || redeemedToken === token) return;
        setRedeemedToken(token);

        // Strip first — even a failing exchange must leave no token in the URL.
        void navigate({ to: '/team/login', search: {}, replace: true });

        exchange.mutate(token, {
            onSuccess: () => {
                void navigate({ to: '/team', replace: true });
            },
            onError: () => {
                setPhase('failed');
            },
        });
        // Runs once per token: the redemption must happen exactly once, so the
        // mutation instance is deliberately excluded from the dependencies.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [token, navigate]);

    if (phase === 'failed') {
        return (
            <Screen titleKey="teamLogin.errorTitle" descriptionKey="teamLogin.errorDescription">
                <BackLink />
            </Screen>
        );
    }

    if (redeemedToken) {
        return (
            <Screen
                titleKey="teamLogin.exchangingTitle"
                descriptionKey="teamLogin.exchangingDescription"
            />
        );
    }

    return (
        <Screen
            titleKey="teamLogin.neutralTitle"
            descriptionKey="teamLogin.neutralDescription"
        >
            <BackLink />
        </Screen>
    );
}
