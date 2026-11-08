import { zodResolver } from '@hookform/resolvers/zod';
import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import {
    useAddCoOrganizer,
    useOrganizers,
} from '@/features/tournaments/hooks/organizers-queries.hook.ts';
import {
    coOrganizerSchema,
    type CoOrganizerFormValues,
} from '@/features/tournaments/organizer-schemas.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError, localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface OrganizersSectionProps {
    tournamentId: string;
    /** Only the lead organizer adds co-organizers; co-organizers see the list alone. */
    isLead: boolean;
}

/** The backend anchors every add-co-organizer problem to the `email` field. */
const SERVER_ERROR_ANCHORS = { fields: { email: 'email' } } as const;

/**
 * The organizers of a tournament: the lead organizer and the co-organizers in the order
 * they were added. The lead also gets an inline form to add a co-organizer by account
 * email; access is granted at once, so the helper text says no invitation is sent.
 */
export function OrganizersSection({ tournamentId, isLead }: OrganizersSectionProps) {
    const { t } = useI18n();
    const organizersQuery = useOrganizers(tournamentId);
    const addCoOrganizer = useAddCoOrganizer(tournamentId);
    const form = useForm<CoOrganizerFormValues>({
        resolver: zodResolver(coOrganizerSchema(t)),
        defaultValues: { email: '' },
    });

    const onSubmit = form.handleSubmit(async (values) => {
        try {
            await addCoOrganizer.mutateAsync(values.email);
            form.reset();
        } catch (error) {
            applyServerError(form.setError, error, t, SERVER_ERROR_ANCHORS);
        }
    });

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

            <ul className="flex flex-col gap-2">
                <OrganizerRow email={lead.email} role={t('organizers.lead')} />
                {coOrganizers.map((coOrganizer) => (
                    <OrganizerRow
                        key={coOrganizer.email}
                        email={coOrganizer.email}
                        role={t('organizers.coOrganizer')}
                    />
                ))}
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
        </section>
    );
}

/** One organizer in the list: the account email and the role on this tournament. */
function OrganizerRow({ email, role }: { email: string; role: string }) {
    return (
        <li className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-card p-3">
            <span className="font-medium">{email}</span>
            <Badge variant="secondary">{role}</Badge>
        </li>
    );
}
