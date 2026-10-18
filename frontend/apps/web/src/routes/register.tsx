import { createFileRoute } from '@tanstack/react-router';

import { RegisterForm } from '@/features/auth/components/register-form.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface RegisterSearch {
    /** Raw invite token from an invite link (`/register?invite=…`). */
    invite?: string;
}

function RegisterPage() {
    const { t } = useI18n();
    const { invite } = Route.useSearch();

    return (
        <section className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
                <h1 className="text-2xl font-semibold">{t('register.title')}</h1>
                <p className="text-muted-foreground">{t('register.description')}</p>
            </div>
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
