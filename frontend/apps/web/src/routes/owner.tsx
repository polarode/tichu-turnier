import { createFileRoute, redirect } from '@tanstack/react-router';

import { sessionQueryOptions } from '@/features/auth/hooks/auth-queries.hook.ts';
import {
    OWNER_CONSOLE_TABS,
    OwnerConsole,
    type OwnerConsoleTab,
} from '@/features/owner-console/components/owner-console.component.tsx';

/**
 * Owner-only console, an area of its own next to admin, team and public. Like `/admin`
 * it sends signed-out visitors to login; the component decides between the console and
 * the localized forbidden state from the session role, while the backend enforces the
 * role on every request.
 */
export const Route = createFileRoute('/owner')({
    beforeLoad: async ({ context }) => {
        const session = await context.queryClient.ensureQueryData(sessionQueryOptions);
        if (session === null) {
            throw redirect({ to: '/login' });
        }
    },
    // An explicit undefined: the router merges the result over the raw search, so an
    // omitted key would let an unknown ?tab= through.
    validateSearch: (search: Record<string, unknown>): OwnerConsoleSearch => ({
        tab: isOwnerConsoleTab(search.tab) ? search.tab : undefined,
    }),
    component: OwnerConsoleRoute,
});

interface OwnerConsoleSearch {
    tab?: OwnerConsoleTab;
}

function isOwnerConsoleTab(value: unknown): value is OwnerConsoleTab {
    return OWNER_CONSOLE_TABS.some((tab) => tab === value);
}

function OwnerConsoleRoute() {
    const { tab = 'accounts' } = Route.useSearch();
    const navigate = Route.useNavigate();
    return (
        <OwnerConsole
            tab={tab}
            onTabChange={(next) => void navigate({ search: { tab: next }, replace: true })}
        />
    );
}
