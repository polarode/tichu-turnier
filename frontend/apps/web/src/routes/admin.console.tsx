import { createFileRoute } from '@tanstack/react-router';

import { OwnerConsole } from '@/features/owner-console/components/owner-console.component.tsx';

/**
 * Owner-only console. The parent `/admin` guard already resolves the session; the
 * component decides between the console and the localized forbidden state from the
 * session role, while the backend enforces the role on every request.
 */
export const Route = createFileRoute('/admin/console')({
    component: OwnerConsole,
});
