import { createFileRoute } from '@tanstack/react-router';

import { CHANGELOG } from '@/features/changelog/changelog.ts';
import { Changelog } from '@/features/changelog/components/changelog.component.tsx';
import { BackLink } from '@/shared/components/back-link.component.tsx';

/**
 * Admin changelog. The parent `/admin` guard already resolves the session and sends
 * signed-out visitors to login, so this route only renders the shipped entries.
 */
export const Route = createFileRoute('/admin/changelog')({
    component: ChangelogRoute,
});

function ChangelogRoute() {
    return (
        <div className="flex flex-col gap-4">
            <BackLink to="/admin" labelKey="dashboard.backLink" />
            <Changelog entries={CHANGELOG} />
        </div>
    );
}
