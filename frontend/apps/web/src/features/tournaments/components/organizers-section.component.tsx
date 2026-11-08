import { type ReactNode, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from '@tanstack/react-router';
import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import type { CoOrganizer } from '@/domain/organizers.domain.ts';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import {
    useAddCoOrganizer,
    useLeaveTournament,
    useOrganizers,
    useRemoveCoOrganizer,
} from '@/features/tournaments/hooks/organizers-queries.hook.ts';
import {
    coOrganizerSchema,
    type CoOrganizerFormValues,
} from '@/features/tournaments/organizer-schemas.ts';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError, localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { useNoticeStore } from '@/shared/stores/notice.store.ts';

interface OrganizersSectionProps {
    tournamentId: string;
    /** Shown in the notice after the current admin leaves the tournament. */
    tournamentName: string;
    /** Only the lead organizer adds and removes co-organizers; a co-organizer may leave. */
    isLead: boolean;
}

/** The backend anchors every add-co-organizer problem to the `email` field. */
const SERVER_ERROR_ANCHORS = { fields: { email: 'email' } } as const;

/**
 * The organizers of a tournament: the lead organizer and the co-organizers in the order
 * they were added. The lead also gets an inline form to add a co-organizer by account
 * email (access is granted at once, so no invitation is sent), and a remove action per
 * co-organizer. A co-organizer gets a leave action on their own row. Disabled accounts
 * are marked on their row.
 */
export function OrganizersSection({
    tournamentId,
    tournamentName,
    isLead,
}: OrganizersSectionProps) {
    const { t } = useI18n();
    const organizersQuery = useOrganizers(tournamentId);
    const addCoOrganizer = useAddCoOrganizer(tournamentId);
    const removeCoOrganizer = useRemoveCoOrganizer(tournamentId);
    const leaveTournament = useLeaveTournament(tournamentId);
    const session = useSession();
    const navigate = useNavigate();
    const showNotice = useNoticeStore((state) => state.show);
    const dismissNotice = useNoticeStore((state) => state.dismiss);
    const currentAdminId = session.data?.id;
    const form = useForm<CoOrganizerFormValues>({
        resolver: zodResolver(coOrganizerSchema(t)),
        defaultValues: { email: '' },
    });

    const [confirmRemove, setConfirmRemove] = useState<CoOrganizer | null>(null);
    const [confirmLeave, setConfirmLeave] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const onSubmit = form.handleSubmit(async (values) => {
        try {
            await addCoOrganizer.mutateAsync(values.email);
            form.reset();
        } catch (error) {
            applyServerError(form.setError, error, t, SERVER_ERROR_ANCHORS);
        }
    });

    const remove = async (coOrganizer: CoOrganizer) => {
        try {
            await removeCoOrganizer.mutateAsync(coOrganizer.adminUserId);
            setConfirmRemove(null);
            setActionError(null);
        } catch (error) {
            setActionError(localizedErrorMessage(error, t));
        }
    };

    const leave = async () => {
        if (!currentAdminId) return;
        // Set before the request: the leave's own live event may 404 the tournament query and
        // redirect first, and the lost-access notice must not take this one's place.
        showNotice({ key: 'organizers.notice.left', values: { name: tournamentName } });
        try {
            await leaveTournament.mutateAsync(currentAdminId);
            await navigate({ to: '/admin' });
        } catch (error) {
            dismissNotice();
            setActionError(localizedErrorMessage(error, t));
        }
    };

    if (organizersQuery.isPending) {
        return <p className="text-muted-foreground">{t('common.loading')}</p>;
    }
    if (organizersQuery.isError) {
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(organizersQuery.error, t)}
            </p>
        );
    }

    const { lead, coOrganizers } = organizersQuery.data;
    const pending = addCoOrganizer.isPending;

    return (
        <section aria-labelledby="organizers-heading" className="flex flex-col gap-3">
            <h2 id="organizers-heading" className="text-lg font-semibold">
                {t('organizers.section.title')}
            </h2>

            <FormAlert message={actionError ?? undefined} />

            <ul className="flex flex-col gap-2">
                <OrganizerRow
                    email={lead.email}
                    role={t('organizers.lead')}
                    disabled={lead.disabled}
                />
                {coOrganizers.map((coOrganizer) => {
                    const isOwnRow = coOrganizer.adminUserId === currentAdminId;
                    let action: ReactNode = null;
                    if (isLead) {
                        action = (
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                    setActionError(null);
                                    setConfirmRemove(coOrganizer);
                                }}
                            >
                                {t('organizers.remove')}
                            </Button>
                        );
                    } else if (isOwnRow) {
                        action = (
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                    setActionError(null);
                                    setConfirmLeave(true);
                                }}
                            >
                                {t('organizers.leave')}
                            </Button>
                        );
                    }
                    return (
                        <OrganizerRow
                            key={coOrganizer.adminUserId}
                            email={coOrganizer.email}
                            role={t('organizers.coOrganizer')}
                            disabled={coOrganizer.disabled}
                            action={action}
                        />
                    );
                })}
            </ul>
            {coOrganizers.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('organizers.empty')}</p>
            ) : null}

            {isLead ? (
                <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
                    <p className="text-sm text-muted-foreground">{t('organizers.form.helper')}</p>
                    <FormAlert message={form.formState.errors.root?.message} />
                    <div className="flex max-w-md flex-wrap items-end gap-2">
                        <div className="flex-1">
                            <FormField
                                id="co-organizer-email"
                                type="email"
                                autoComplete="off"
                                placeholder={t('organizers.form.placeholder')}
                                label={t('organizers.form.email')}
                                error={form.formState.errors.email?.message}
                                {...form.register('email')}
                            />
                        </div>
                        <Button type="submit" disabled={pending}>
                            {pending ? t('organizers.form.adding') : t('organizers.form.add')}
                        </Button>
                    </div>
                </form>
            ) : null}

            {confirmRemove ? (
                <ConfirmDialog
                    titleKey="organizers.remove.title"
                    description={
                        <>
                            {t('organizers.remove.description')}{' '}
                            <strong className="font-medium text-foreground">
                                {confirmRemove.email}
                            </strong>
                        </>
                    }
                    confirmLabelKey="organizers.remove.confirm"
                    destructive
                    disabled={removeCoOrganizer.isPending}
                    onConfirm={() => remove(confirmRemove)}
                    onCancel={() => setConfirmRemove(null)}
                />
            ) : null}

            {confirmLeave ? (
                <ConfirmDialog
                    titleKey="organizers.leave.title"
                    descriptionKey="organizers.leave.description"
                    confirmLabelKey="organizers.leave.confirm"
                    destructive
                    disabled={leaveTournament.isPending}
                    onConfirm={() => leave()}
                    onCancel={() => setConfirmLeave(false)}
                />
            ) : null}
        </section>
    );
}

/** One organizer in the list: the account email, the role on this tournament, and an optional action. */
function OrganizerRow({
    email,
    role,
    disabled,
    action,
}: {
    email: string;
    role: string;
    disabled: boolean;
    action?: ReactNode;
}) {
    const { t } = useI18n();
    return (
        <li className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-card p-3">
            <span className="font-medium">{email}</span>
            <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary">{role}</Badge>
                {disabled ? <Badge variant="outline">{t('organizers.disabled')}</Badge> : null}
                {action}
            </div>
        </li>
    );
}
