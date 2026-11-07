import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@tichu/ui/button.tsx';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@tichu/ui/select.tsx';
import { Controller, useForm, type Control } from 'react-hook-form';

import {
    DOUBLE_WIN_BONUS_OPTIONS,
    GRAND_TICHU_BONUS_OPTIONS,
    TOTAL_ROUNDS_DEFAULT,
} from '@/domain/tournament.domain.ts';
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
    fields: {
        name: 'name',
        maxTeams: 'maxTeams',
        grandTichuBonus: 'grandTichuBonus',
        doubleWinBonus: 'doubleWinBonus',
        totalRounds: 'totalRounds',
    },
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
 * count, the total rounds and the grand Tichu and double win bonuses. Client validation is instant; server failures are
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
            doubleWinBonus: 200,
            totalRounds: TOTAL_ROUNDS_DEFAULT,
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
            <FormField
                id="tournament-total-rounds"
                type="number"
                label={t('tournament.form.totalRounds')}
                error={form.formState.errors.totalRounds?.message}
                {...form.register('totalRounds', { valueAsNumber: true })}
            />

            <BonusSelect
                id="tournament-grand-tichu-bonus"
                name="grandTichuBonus"
                label={t('tournament.form.grandTichuBonus')}
                options={GRAND_TICHU_BONUS_OPTIONS}
                control={form.control}
                error={form.formState.errors.grandTichuBonus?.message}
            />
            <BonusSelect
                id="tournament-double-win-bonus"
                name="doubleWinBonus"
                label={t('tournament.form.doubleWinBonus')}
                options={DOUBLE_WIN_BONUS_OPTIONS}
                control={form.control}
                error={form.formState.errors.doubleWinBonus?.message}
            />

            <Button type="submit" disabled={pending}>
                {pending ? t(pendingLabelKey) : t(submitLabelKey)}
            </Button>
        </form>
    );
}

interface BonusSelectProps {
    id: string;
    name: 'grandTichuBonus' | 'doubleWinBonus';
    label: string;
    options: readonly number[];
    control: Control<TournamentFormValues>;
    error: string | undefined;
}

/** A labelled dropdown for one of the bonus settings, whose options are point amounts. */
function BonusSelect({ id, name, label, options, control, error }: BonusSelectProps) {
    const errorId = `${id}-error`;
    return (
        <div className="flex flex-col gap-1.5">
            <label htmlFor={id} className="text-sm font-medium">
                {label}
            </label>
            <Controller
                control={control}
                name={name}
                render={({ field }) => (
                    // Select values are strings; the form value is a number.
                    <Select value={String(field.value)} onValueChange={(value) => field.onChange(Number(value))}>
                        <SelectTrigger
                            id={id}
                            ref={field.ref}
                            onBlur={field.onBlur}
                            aria-invalid={error ? true : undefined}
                            aria-describedby={error ? errorId : undefined}
                        >
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {options.map((bonus) => (
                                <SelectItem key={bonus} value={String(bonus)}>
                                    {bonus}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                )}
            />
            {error ? (
                <p id={errorId} role="alert" className="text-sm text-destructive">
                    {error}
                </p>
            ) : null}
        </div>
    );
}
