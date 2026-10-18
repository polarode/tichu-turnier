import { createFileRoute } from '@tanstack/react-router';

import { LoginForm } from '@/features/auth/components/login-form.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

function LoginPage() {
    return (
        <section className="flex flex-col gap-4">
            <PageHeader titleKey="login.title" descriptionKey="login.description" />
            <LoginForm />
        </section>
    );
}

export const Route = createFileRoute('/login')({
    component: LoginPage,
});
