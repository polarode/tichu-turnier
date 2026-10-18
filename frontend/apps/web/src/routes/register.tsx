import { createFileRoute } from '@tanstack/react-router';

import { RegisterForm } from '@/features/auth/components/register-form.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

interface RegisterSearch {
    /** Raw invite token from an invite link (`/register?invite=…`). */
    invite?: string;
}

function RegisterPage() {
    const { invite } = Route.useSearch();

    return (
        <section className="flex flex-col gap-4">
            <PageHeader titleKey="register.title" descriptionKey="register.description" />
            <RegisterForm inviteToken={invite} />
        </section>
    );
}

export const Route = createFileRoute('/register')({
    // Invite links land here with `?invite=<token>`; ignore anything empty or non-text.
    validateSearch: (search: Record<string, unknown>): RegisterSearch =>
        typeof search.invite === 'string' && search.invite.length > 0
            ? { invite: search.invite }
            : {},
    component: RegisterPage,
});
