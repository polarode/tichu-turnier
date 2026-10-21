import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import {
    tournamentFormSchema,
    type TournamentFormValues,
} from '@/features/tournaments/tournament-schemas.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

/**
 * How server problems anchor to form fields: a problem carrying a `field` member maps
 * by field name (Bean-validation and InvalidField problems do). MAX_TEAMS_BELOW_TEAM_COUNT
 * is listed by code only as a defensive fallback — the backend currently anchors it to
 * `maxTeams` itself, so the `fields` map handles it.
 */
const SERVER_ERROR_ANCHORS = {
    fields: { name: 'name', maxTeams: 'maxTeams' },
    codes: { MAX_TEAMS_BELOW_TEAM_COUNT: 'maxTeams' },
} as const;

interface TournamentFormProps {
    /** Values shown when the form opens; edit passes the loaded tournament. */
    defaultValues?: Partial<TournamentFormValues>;
    /** Catalog key for the submit button's idle label. */
    submitLabelKey: TranslationKey;
    /** Catalog key for the submit button's in-flight label. */
    pendingLabelKey: TranslationKey;
    /** Runs on a validated submission; throws propagate into the server-error routing. */
    onSubmit: (values: TournamentFormValues) => Promise<void>;
}

/**
 * The tournament settings form shared by create and edit: name, description, max team
 * count and the grand-Tichu toggle. Client validation is instant; server failures are
 * localized and anchored to the offending field where the code allows it.
 */
export function TournamentForm({
    defaultValues,
    submitLabelKey,
    pendingLabelKey,
    onSubmit,
}: TournamentFormProps) {
    const { t } = useI18n();
    const form = useForm<TournamentFormValues>({
        resolver: zodResolver(tournamentFormSchema(t)),
        defaultValues: {
            name: '',
            description: '',
            maxTeams: 8,
            allowGrandTichu: false,
            ...defaultValues,
        },
    });

    const handleSubmit = form.handleSubmit(async (values) => {
        try {
            await onSubmit(values);
        } catch (error) {
            applyServerError(form.setError, error, t, SERVER_ERROR_ANCHORS);
        }
    });

    const rootError = form.formState.errors.root?.message;
    const pending = form.formState.isSubmitting;

    return (
        <form onSubmit={handleSubmit} noValidate className="flex max-w-sm flex-col gap-4">
            <FormAlert message={rootError} />

            <FormField
                id="tournament-name"
                type="text"
                label={t('tournament.form.name')}
                error={form.formState.errors.name?.message}
                {...form.register('name')}
            />
            <FormField
                id="tournament-description"
                type="text"
                label={t('tournament.form.description')}
                error={form.formState.errors.description?.message}
                {...form.register('description')}
            />
            <FormField
                id="tournament-max-teams"
                type="number"
                label={t('tournament.form.maxTeams')}
                error={form.formState.errors.maxTeams?.message}
                {...form.register('maxTeams', { valueAsNumber: true })}
            />

            <div className="flex flex-col gap-1.5">
                <label htmlFor="tournament-grand-tichu" className="flex items-center gap-2 text-sm font-medium">
                    <input
                        id="tournament-grand-tichu"
                        type="checkbox"
                        className="size-4 accent-primary"
                        {...form.register('allowGrandTichu')}
                    />
                    {t('tournament.form.allowGrandTichu')}
                </label>
            </div>

            <Button type="submit" disabled={pending}>
                {pending ? t(pendingLabelKey) : t(submitLabelKey)}
            </Button>
        </form>
    );
}
