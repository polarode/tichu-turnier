import { useId, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@tichu/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@tichu/ui/dialog.tsx';
import { useForm } from 'react-hook-form';

import type { Team } from '@/domain/team.domain.ts';
import { useWithdrawTeam } from '@/features/teams/hooks/team-queries.hook.ts';
import { withdrawalFormSchema, type WithdrawalFormValues } from '@/features/teams/team-schemas.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { FORFEIT_COMPENSATIONS } from '@/vocabulary/forfeit-compensation.enum.ts';
import { WITHDRAWAL_REASONS } from '@/vocabulary/withdrawal-reason.enum.ts';

interface WithdrawTeamDialogProps {
    tournamentId: string;
    team: Team;
    /** Closes the dialog; a successful withdrawal closes it. */
    onClose: () => void;
}

/**
 * Withdraws a team (GLOSSARY.md: Withdrawal, ADR 0011). The organizer picks the reason and
 * the Forfeit Compensation explicitly, with no default for either. The warning states what
 * happens to the team; the hint states which games the compensation applies to. A refused
 * withdrawal stays in the dialog as a localized message.
 */
export function WithdrawTeamDialog({ tournamentId, team, onClose }: WithdrawTeamDialogProps) {
    const { t } = useI18n();
    const withdraw = useWithdrawTeam(tournamentId);
    const [error, setError] = useState<unknown>(null);
    const ids = useId();
    const reasonErrorId = `${ids}-reason-error`;
    const compensationHintId = `${ids}-compensation-hint`;
    const compensationErrorId = `${ids}-compensation-error`;

    const form = useForm<WithdrawalFormValues>({
        resolver: zodResolver(withdrawalFormSchema(t)),
    });
    const { errors, isSubmitting } = form.formState;

    const submit = form.handleSubmit(async (values) => {
        setError(null);
        try {
            await withdraw.mutateAsync({ teamId: team.id, input: values });
            onClose();
        } catch (e) {
            setError(e);
        }
    });

    const compensationDescribedBy = [
        compensationHintId,
        errors.compensation ? compensationErrorId : null,
    ]
        .filter(Boolean)
        .join(' ');

    return (
        <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>{t('teams.withdraw.title')}</DialogTitle>
                    <DialogDescription>{team.name}</DialogDescription>
                </DialogHeader>

                <form onSubmit={submit} noValidate className="flex flex-col gap-4">
                    <fieldset
                        className="flex flex-col gap-2"
                        role="radiogroup"
                        aria-required="true"
                        aria-describedby={errors.reason ? reasonErrorId : undefined}
                    >
                        <legend className="mb-1 text-sm font-medium">
                            {t('teams.withdraw.reasonLegend')}
                        </legend>
                        {WITHDRAWAL_REASONS.map((reason) => (
                            <label key={reason} className="flex items-center gap-2 text-sm">
                                <input
                                    type="radio"
                                    value={reason}
                                    className="size-4"
                                    aria-invalid={errors.reason ? true : undefined}
                                    {...form.register('reason')}
                                />
                                {t(`teams.withdraw.reason.${reason}`)}
                            </label>
                        ))}
                        {errors.reason ? (
                            <p id={reasonErrorId} role="alert" className="text-sm text-destructive">
                                {errors.reason.message}
                            </p>
                        ) : null}
                    </fieldset>

                    <fieldset
                        className="flex flex-col gap-2"
                        role="radiogroup"
                        aria-required="true"
                        aria-describedby={compensationDescribedBy}
                    >
                        <legend className="mb-1 text-sm font-medium">
                            {t('teams.withdraw.compensationLegend')}
                        </legend>
                        <p id={compensationHintId} className="text-sm text-muted-foreground">
                            {t('teams.withdraw.compensationHint')}
                        </p>
                        {FORFEIT_COMPENSATIONS.map((compensation) => (
                            <label key={compensation} className="flex items-center gap-2 text-sm">
                                <input
                                    type="radio"
                                    value={compensation}
                                    className="size-4"
                                    aria-invalid={errors.compensation ? true : undefined}
                                    {...form.register('compensation')}
                                />
                                {t(`teams.withdraw.compensation.${compensation}`)}
                            </label>
                        ))}
                        {errors.compensation ? (
                            <p
                                id={compensationErrorId}
                                role="alert"
                                className="text-sm text-destructive"
                            >
                                {errors.compensation.message}
                            </p>
                        ) : null}
                    </fieldset>

                    <p className="rounded-md border border-border bg-muted p-3 text-sm text-foreground">
                        {t('teams.withdraw.warning')}
                    </p>

                    <FormAlert
                        message={error !== null ? localizedErrorMessage(error, t) : undefined}
                    />

                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={onClose}>
                            {t('common.cancel')}
                        </Button>
                        <Button type="submit" variant="destructive" disabled={isSubmitting}>
                            {isSubmitting
                                ? t('teams.withdraw.submitting')
                                : t('teams.withdraw.submit')}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
