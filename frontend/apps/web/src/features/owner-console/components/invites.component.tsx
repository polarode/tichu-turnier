import { zodResolver } from '@hookform/resolvers/zod';
import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';
import { Input } from '@tichu/ui/input.tsx';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import type { AdminInvite, IssuedInvite } from '@/domain/admin-account.domain.ts';
import { ConsoleRow } from '@/features/owner-console/components/console-row.component.tsx';
import { ConsoleSection } from '@/features/owner-console/components/console-section.component.tsx';
import { CopyButton } from '@/features/owner-console/components/copy-button.component.tsx';
import {
    useCreateInvite,
    useRevokeInvite,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { useConsoleAction } from '@/features/owner-console/hooks/use-console-action.hook.ts';
import { createInviteSchema, type InviteValues } from '@/features/owner-console/invite-schemas.ts';
import { INVITE_STATUS_LABEL_KEYS } from '@/features/owner-console/labels.ts';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { InviteStatus } from '@/vocabulary/admin.enum.ts';

/**
 * Invite issuance and the issued-invite list. The raw link is returned exactly once at
 * issuance and held only in local state — it is never re-fetchable from the list, so
 * the panel tells the owner to copy it now.
 */
export function Invites({ invites }: { invites: AdminInvite[] }) {
    const { t, locale } = useI18n();
    const createInvite = useCreateInvite();
    const revokeInvite = useRevokeInvite();
    const { error, run } = useConsoleAction(t);
    const [issued, setIssued] = useState<IssuedInvite>();
    const form = useForm<InviteValues>({ resolver: zodResolver(createInviteSchema(t)) });

    const onSubmit = form.handleSubmit(async (values) => {
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

    return (
        <ConsoleSection
            title={t('ownerConsole.invites.title')}
            description={t('ownerConsole.invites.description')}
            error={form.formState.errors.root?.message ?? error}
        >
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
                        <ConsoleRow key={invite.id}>
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
                                    {`${t('ownerConsole.invites.expires')}: ${invite.expiresAt.toLocaleDateString(locale)}`}
                                </span>
                            </div>
                            {invite.status === InviteStatus.PENDING ? (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    disabled={revokeInvite.isPending}
                                    onClick={() => void run(() => revokeInvite.mutateAsync(invite.id))}
                                >
                                    {t('ownerConsole.invites.revoke')}
                                </Button>
                            ) : null}
                        </ConsoleRow>
                    ))}
                </ul>
            )}
        </ConsoleSection>
    );
}
