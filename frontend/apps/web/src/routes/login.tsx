import { createFileRoute } from '@tanstack/react-router';

import { LoginForm } from '@/features/auth/components/login-form.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

function LoginPage() {
    const { t } = useI18n();

    return (
        <section className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
                <h1 className="text-2xl font-semibold">{t('login.title')}</h1>
                <p className="text-muted-foreground">{t('login.description')}</p>
            </div>
            <LoginForm />
        </section>
    );
}

export const Route = createFileRoute('/login')({
    component: LoginPage,
});
