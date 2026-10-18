import { createFileRoute } from '@tanstack/react-router';

import { RegisterForm } from '@/features/auth/components/register-form.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

function RegisterPage() {
    const { t } = useI18n();

    return (
        <section className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
                <h1 className="text-2xl font-semibold">{t('register.title')}</h1>
                <p className="text-muted-foreground">{t('register.description')}</p>
            </div>
            <RegisterForm />
        </section>
    );
}

export const Route = createFileRoute('/register')({
    component: RegisterPage,
});
