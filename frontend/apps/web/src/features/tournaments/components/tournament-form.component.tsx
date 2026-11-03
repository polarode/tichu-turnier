import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@tichu/ui/button.tsx';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@tichu/ui/select.tsx';
import { Controller, useForm } from 'react-hook-form';

import { GRAND_TICHU_BONUS_OPTIONS } from '@/domain/tournament.domain.ts';
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
    fields: { name: 'name', maxTeams: 'maxTeams', grandTichuBonus: 'grandTichuBonus' },
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
            grandTichuBonus: 200,
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
    const bonusError = form.formState.errors.grandTichuBonus?.message;
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
                <label htmlFor="tournament-grand-tichu-bonus" className="text-sm font-medium">
                    {t('tournament.form.grandTichuBonus')}
                </label>
                <Controller
                    control={form.control}
                    name="grandTichuBonus"
                    render={({ field }) => (
                        // Select values are strings; the form value is a number.
                        <Select value={String(field.value)} onValueChange={(value) => field.onChange(Number(value))}>
                            <SelectTrigger
                                id="tournament-grand-tichu-bonus"
                                ref={field.ref}
                                onBlur={field.onBlur}
                                aria-invalid={bonusError ? true : undefined}
                                aria-describedby={bonusError ? 'tournament-grand-tichu-bonus-error' : undefined}
                            >
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {GRAND_TICHU_BONUS_OPTIONS.map((bonus) => (
                                    <SelectItem key={bonus} value={String(bonus)}>
                                        {bonus}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    )}
                />
                {bonusError ? (
                    <p id="tournament-grand-tichu-bonus-error" role="alert" className="text-sm text-destructive">
                        {bonusError}
                    </p>
                ) : null}
            </div>

            <Button type="submit" disabled={pending}>
                {pending ? t(pendingLabelKey) : t(submitLabelKey)}
            </Button>
        </form>
    );
}
