import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from '@tanstack/react-router';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import { createRegisterSchema, type RegisterValues } from '@/features/auth/auth-schemas.ts';
import { useRegisterAdmin } from '@/features/auth/hooks/auth-queries.hook.ts';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Account creation: validates locally, then registers and signs in, landing in the admin area. */
export function RegisterForm() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const register = useRegisterAdmin();
    const form = useForm<RegisterValues>({ resolver: zodResolver(createRegisterSchema(t)) });

    const onSubmit = form.handleSubmit(async (values) => {
        try {
            await register.mutateAsync(values);
            await navigate({ to: '/admin' });
        } catch (error) {
            // The backend does not field-anchor EMAIL_ALREADY_REGISTERED; attach it to email.
            applyServerError(form.setError, error, t, {
                codes: { EMAIL_ALREADY_REGISTERED: 'email' },
            });
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
                autoComplete="new-password"
                label={t('auth.password')}
                error={form.formState.errors.password?.message}
                {...form.register('password')}
            />

            <Button type="submit" disabled={register.isPending}>
                {register.isPending ? t('register.pending') : t('register.submit')}
            </Button>

            <p className="text-sm text-muted-foreground">
                {t('register.haveAccount')}{' '}
                <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                    {t('register.loginLink')}
                </Link>
            </p>
        </form>
    );
}
