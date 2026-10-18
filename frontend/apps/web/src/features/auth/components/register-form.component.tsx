import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from '@tanstack/react-router';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import { createRegisterSchema, type RegisterValues } from '@/features/auth/auth-schemas.ts';
import { useRegisterAdmin } from '@/features/auth/hooks/auth-queries.hook.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface RegisterFormProps {
    /** Raw token from an invite link (`/register?invite=…`); activates the account on success. */
    inviteToken?: string;
}

/**
 * Account creation. A plain registration creates a pending account and lands on the
 * awaiting-approval state; an invited registration is activated immediately and signs
 * the organizer straight in.
 */
export function RegisterForm({ inviteToken }: RegisterFormProps) {
    const { t } = useI18n();
    const navigate = useNavigate();
    const registerAdmin = useRegisterAdmin();
    const form = useForm<RegisterValues>({ resolver: zodResolver(createRegisterSchema(t)) });

    const onSubmit = form.handleSubmit(async (values) => {
        try {
            const outcome = await registerAdmin.mutateAsync({ ...values, inviteToken });
            if (outcome.kind === 'active') {
                await navigate({ to: '/admin' });
            } else if (outcome.kind === 'pending') {
                await navigate({ to: '/pending-approval' });
            } else {
                // The invite activated the account but the follow-up sign-in failed; the
                // account is usable, so point at sign-in rather than implying it failed.
                form.setError('root', { type: 'server', message: t('register.signInFailed') });
            }
        } catch (error) {
            // The backend field-anchors neither of these; attach the address-related
            // ones to the email input and leave the rest as a form-level message.
            applyServerError(form.setError, error, t, {
                codes: {
                    EMAIL_ALREADY_REGISTERED: 'email',
                    INVITE_EMAIL_MISMATCH: 'email',
                },
            });
        }
    });

    const rootError = form.formState.errors.root?.message;

    return (
        <form onSubmit={onSubmit} noValidate className="flex max-w-sm flex-col gap-4">
            {inviteToken ? (
                <p className="rounded-md bg-accent px-3 py-2 text-sm text-accent-foreground">
                    {t('register.inviteNotice')}
                </p>
            ) : null}

            <FormAlert message={rootError} />

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

            <Button type="submit" disabled={registerAdmin.isPending}>
                {registerAdmin.isPending ? t('register.pending') : t('register.submit')}
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
