import { createFileRoute, redirect } from '@tanstack/react-router';

/** There is no separate landing page: the public area is the entry point. */
export const Route = createFileRoute('/')({
    beforeLoad: () => {
        throw redirect({ to: '/public' });
    },
});
