import { zodResolver } from '@hookform/resolvers/zod';
import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';
import { Input } from '@tichu/ui/input.tsx';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import type { AdminInvite, IssuedInvite } from '@/domain/admin-account.domain.ts';
import { CopyButton } from '@/features/owner-console/components/copy-button.component.tsx';
import {
    useCreateInvite,
    useRevokeInvite,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { createInviteSchema, type InviteValues } from '@/features/owner-console/invite-schemas.ts';
import { INVITE_STATUS_LABEL_KEYS } from '@/features/owner-console/labels.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError, localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { InviteStatus } from '@/vocabulary/admin.enum.ts';

/** Formats a timestamp as a short date; the exact locale format is not significant. */
function formatDate(date: Date): string {
    return date.toLocaleDateString();
}

/**
 * Invite issuance and the issued-invite list. The raw link is returned exactly once at
 * issuance and held only in local state — it is never re-fetchable from the list, so
 * the panel tells the owner to copy it now.
 */
export function Invites({ invites }: { invites: AdminInvite[] }) {
    const { t } = useI18n();
    const createInvite = useCreateInvite();
    const revokeInvite = useRevokeInvite();
    const [issued, setIssued] = useState<IssuedInvite>();
    const [error, setError] = useState<string>();
    const form = useForm<InviteValues>({ resolver: zodResolver(createInviteSchema(t)) });

    const onSubmit = form.handleSubmit(async (values) => {
        setError(undefined);
        try {
            const created = await createInvite.mutateAsync(values.email);
            setIssued(created);
            form.reset();
        } catch (failure) {
            applyServerError(form.setError, failure, t, {
                codes: { EMAIL_ALREADY_REGISTERED: 'email' },
            });
        }
    });

    const onRevoke = async (id: string) => {
        setError(undefined);
        try {
            await revokeInvite.mutateAsync(id);
        } catch (failure) {
            setError(localizedErrorMessage(failure, t));
        }
    };

    return (
        <section className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
                <h2 className="text-xl font-semibold">{t('ownerConsole.invites.title')}</h2>
                <p className="text-sm text-muted-foreground">
                    {t('ownerConsole.invites.description')}
                </p>
            </div>

            <form onSubmit={onSubmit} noValidate className="flex max-w-md items-end gap-2">
                <div className="flex-1">
                    <FormField
                        id="invite-email"
                        type="email"
                        autoComplete="off"
                        label={t('ownerConsole.invites.email')}
                        error={form.formState.errors.email?.message}
                        {...form.register('email')}
                    />
                </div>
                <Button type="submit" disabled={createInvite.isPending}>
                    {createInvite.isPending
                        ? t('ownerConsole.invites.issuing')
                        : t('ownerConsole.invites.issue')}
                </Button>
            </form>

            <FormAlert message={form.formState.errors.root?.message ?? error} />

            {issued ? (
                <div className="flex flex-col gap-2 rounded-md border border-border bg-accent/40 p-3">
                    <p className="font-medium">{t('ownerConsole.invites.issued.title')}</p>
                    <p className="text-sm text-muted-foreground">
                        {t('ownerConsole.invites.issued.description')}
                    </p>
                    <div className="flex flex-wrap items-center gap-2">
                        <Input
                            readOnly
                            value={issued.link}
                            aria-label={t('ownerConsole.invites.issued.title')}
                            className="max-w-md flex-1"
                        />
                        <CopyButton value={issued.link} />
                    </div>
                </div>
            ) : null}

            {invites.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('ownerConsole.invites.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {invites.map((invite) => (
                        <li
                            key={invite.id}
                            className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border p-3"
                        >
                            <div className="flex flex-wrap items-center gap-3">
                                <span className="font-medium">{invite.email}</span>
                                <Badge
                                    variant={
                                        invite.status === InviteStatus.PENDING
                                            ? 'warning'
                                            : 'secondary'
                                    }
                                >
                                    {t(INVITE_STATUS_LABEL_KEYS[invite.status])}
                                </Badge>
                                <span className="text-sm text-muted-foreground">
                                    {`${t('ownerConsole.invites.expires')}: ${formatDate(invite.expiresAt)}`}
                                </span>
                            </div>
                            {invite.status === InviteStatus.PENDING ? (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    disabled={revokeInvite.isPending}
                                    onClick={() => void onRevoke(invite.id)}
                                >
                                    {t('ownerConsole.invites.revoke')}
                                </Button>
                            ) : null}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
