import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@tichu/ui/button.tsx';
import { useForm } from 'react-hook-form';

import type { Team } from '@/domain/team.domain.ts';
import {
    teamFormSchema,
    type TeamFormValues,
} from '@/features/teams/team-schemas.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { FormField } from '@/shared/components/form-field.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

/**
 * How server problems anchor to the team form: the backend anchors duplicate names to
 * `name`, `playerOneName` or `playerTwoName`, so the field names map one-to-one. The
 * team capacity is a tournament-level rule the backend leaves unanchored; it routes
 * by code to the form root (the dialog is only reachable when capacity remains, so
 * this is the race-condition fallback behind the disabled add button).
 */
const SERVER_ERROR_ANCHORS = {
    fields: { name: 'name', playerOneName: 'playerOneName', playerTwoName: 'playerTwoName' },
    codes: { TEAM_LIMIT_REACHED: 'root' },
} as const;

interface TeamFormDialogProps {
    /** Whether the dialog is open. */
    open: boolean;
    /** The team being edited; absent in add mode. */
    team?: Team;
    /** Runs on a validated submission; throws propagate into the server-error routing. */
    onSubmit: (values: TeamFormValues) => Promise<void>;
    /** Closes the dialog. */
    onClose: () => void;
}

/**
 * Modal team form shared by add and edit: a team name plus the two players' names.
 * Client validation is instant; server failures (duplicate team or player name, team
 * capacity) are localized and anchored to the offending field where the code allows.
 */
export function TeamFormDialog({ open, team, onSubmit, onClose }: TeamFormDialogProps) {
    const { t } = useI18n();
    const form = useForm<TeamFormValues>({
        resolver: zodResolver(teamFormSchema(t)),
        defaultValues: {
            name: team?.name ?? '',
            playerOneName: team?.players[0]?.name ?? '',
            playerTwoName: team?.players[1]?.name ?? '',
        },
    });

    const handleSubmit = form.handleSubmit(async (values) => {
        try {
            await onSubmit(values);
        } catch (error) {
            applyServerError(form.setError, error, t, SERVER_ERROR_ANCHORS);
        }
    });

    if (!open) return null;

    const titleKey: TranslationKey = team ? 'teams.dialog.editTitle' : 'teams.dialog.addTitle';
    const submitLabelKey: TranslationKey = team ? 'teams.dialog.save' : 'teams.dialog.add';
    const rootError = form.formState.errors.root?.message;
    const pending = form.formState.isSubmitting;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div
                role="dialog"
                aria-modal="true"
                aria-label={t(titleKey)}
                className="w-full max-w-sm rounded-md border border-border bg-background p-6 shadow-lg"
            >
                <h2 className="mb-4 text-lg font-semibold">{t(titleKey)}</h2>
                <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
                    <FormAlert message={rootError} />

                    <FormField
                        id="team-name"
                        type="text"
                        label={t('teams.form.teamName')}
                        error={form.formState.errors.name?.message}
                        {...form.register('name')}
                    />
                    <FormField
                        id="team-player-one"
                        type="text"
                        label={t('teams.form.playerOne')}
                        error={form.formState.errors.playerOneName?.message}
                        {...form.register('playerOneName')}
                    />
                    <FormField
                        id="team-player-two"
                        type="text"
                        label={t('teams.form.playerTwo')}
                        error={form.formState.errors.playerTwoName?.message}
                        {...form.register('playerTwoName')}
                    />

                    <div className="flex justify-end gap-2">
                        <Button type="button" variant="outline" onClick={onClose}>
                            {t('common.cancel')}
                        </Button>
                        <Button type="submit" disabled={pending}>
                            {pending ? t('teams.dialog.saving') : t(submitLabelKey)}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
