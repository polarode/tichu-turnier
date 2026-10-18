import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from '@tanstack/react-router';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import { createLoginSchema, type LoginValues } from '@/features/auth/auth-schemas.ts';
import { useLoginAdmin } from '@/features/auth/hooks/auth-queries.hook.ts';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Sign-in form: client-side validation, localized server errors, redirect to the admin area. */
export function LoginForm() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const login = useLoginAdmin();
    const form = useForm<LoginValues>({ resolver: zodResolver(createLoginSchema(t)) });

    const onSubmit = form.handleSubmit(async (values) => {
        try {
            await login.mutateAsync(values);
            await navigate({ to: '/admin' });
        } catch (error) {
            applyServerError(form.setError, error, t);
        }
    });

    const rootError = form.formState.errors.root?.message;

    return (
        <form onSubmit={onSubmit} noValidate className="flex max-w-sm flex-col gap-4">
            {rootError ? (
                <p
                    role="alert"
                    className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
                >
                    {rootError}
                </p>
            ) : null}

            <FormField
                id="email"
                type="email"
                autoComplete="email"
                label={t('auth.email')}
                error={form.formState.errors.email?.message}
                {...form.register('email')}
            />
            <FormField
                id="password"
                type="password"
                autoComplete="current-password"
                label={t('auth.password')}
                error={form.formState.errors.password?.message}
                {...form.register('password')}
            />

            <Button type="submit" disabled={login.isPending}>
                {login.isPending ? t('login.pending') : t('login.submit')}
            </Button>

            <p className="text-sm text-muted-foreground">
                {t('login.noAccount')}{' '}
                <Link to="/register" className="text-primary underline-offset-4 hover:underline">
                    {t('login.registerLink')}
                </Link>
            </p>
        </form>
    );
}
